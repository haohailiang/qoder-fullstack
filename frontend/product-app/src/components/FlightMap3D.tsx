import { useEffect, useRef, useCallback } from 'react'
import * as Cesium from 'cesium'
import 'cesium/Build/Cesium/Widgets/widgets.css'

interface RouteCity {
  name: string
  lng: number
  lat: number
}

interface FlightData {
  departureCity: string
  arrivalCity: string
  departureLng: number
  departureLat: number
  arrivalLng: number
  arrivalLat: number
  flightNumber: string
  duration: number
}

interface Props {
  flight: FlightData
  routeCities: RouteCity[]
  viewMode: 'overview' | 'planeView'
  animProgress: number
  isPlaying: boolean
  onProgressChange: (value: number) => void
  onPlayToggle: () => void
}

// Cesium Ion 公共 demo token 已过期，清空以避免 401 错误
;(Cesium as any).Ion.defaultAccessToken = ''

// product-app dev server 完整基础 URL（qiankun 模式下浏览器 origin 为 base-app:5173，
// 静态资源必须用完整 URL 从 product-app:7100 获取，否则会被 base-app 的 HTML fallback 拦截）
const PRODUCT_APP_BASE_URL = 'http://localhost:7100'

/**
 * 生成弧线路径点（从起点到终点，中间经过途经城市）
 * 每个点插值一定高度形成弧形效果
 */
function generateArcPoints(
  fromLng: number, fromLat: number,
  toLng: number, toLat: number,
  numPoints: number = 100,
  arcHeight: number = 50000, // 弧度高度（米）
): Cesium.Cartesian3[] {
  const points: Cesium.Cartesian3[] = []
  for (let i = 0; i <= numPoints; i++) {
    const t = i / numPoints
    const lng = fromLng + (toLng - fromLng) * t
    const lat = fromLat + (toLat - fromLat) * t
    // 弧形高度：sin 曲线，中间最高
    const height = Math.sin(t * Math.PI) * arcHeight
    points.push(Cesium.Cartesian3.fromDegrees(lng, lat, height))
  }
  return points
}

/**
 * 根据进度值计算飞机当前位置
 */
function getPlanePositionAtProgress(
  fromLng: number, fromLat: number,
  toLng: number, toLat: number,
  progress: number,
  arcHeight: number = 50000,
): { lng: number; lat: number; height: number } {
  const t = Math.max(0, Math.min(1, progress))
  const lng = fromLng + (toLng - fromLng) * t
  const lat = fromLat + (toLat - fromLat) * t
  const height = Math.sin(t * Math.PI) * arcHeight
  return { lng, lat, height }
}

/**
 * 计算飞机朝向（heading）基于前进方向
 */
function computeHeading(
  fromLng: number, fromLat: number,
  toLng: number, toLat: number,
): number {
  const dLng = (toLng - fromLng) * Math.PI / 180
  const dLat = (toLat - fromLat) * Math.PI / 180
  const y = Math.sin(dLng) * Math.cos(toLat * Math.PI / 180)
  const x =
    Math.cos(fromLat * Math.PI / 180) * Math.sin(toLat * Math.PI / 180) -
    Math.sin(fromLat * Math.PI / 180) * Math.cos(toLat * Math.PI / 180) * Math.cos(dLng)
  return Math.atan2(y, x)
}

function FlightMap3D({ flight, routeCities, viewMode, animProgress, isPlaying, onProgressChange, onPlayToggle }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const viewerRef = useRef<Cesium.Viewer | null>(null)
  const planeEntityRef = useRef<Cesium.Entity | null>(null)
  const arcEntityRef = useRef<Cesium.Entity | null>(null)
  const cityLabelsRef = useRef<Cesium.Entity[]>([])
  const animFrameRef = useRef<number>(0)
  const lastTimeRef = useRef<number>(0)
  const progressRef = useRef<number>(animProgress)

  // 保持 progressRef 同步
  useEffect(() => {
    progressRef.current = animProgress
  }, [animProgress])

  // 初始化 Cesium Viewer
  useEffect(() => {
    if (!containerRef.current || viewerRef.current) return

    try {
      const viewer = new Cesium.Viewer(containerRef.current, {
        baseLayerPicker: false,
        timeline: false,
        animation: false,
        homeButton: false,
        sceneModePicker: false,
        navigationHelpButton: false,
        fullscreenButton: false,
        geocoder: false,
        infoBox: false,
        selectionIndicator: false,
        shadows: false,
        // 禁用依赖 Cesium Ion 的功能（token 已失效）
        terrain: undefined,
        baseLayer: false,
      })

      // 使用高德地图国内 CDN 卫星瓦片（支持 CORS，国内网络可达；OpenStreetMap / CartoDB 在国内不通）
      // 注：高德使用 GCJ-02 坐标系，与 WGS84 航线坐标有轻微偏移，demo 可接受
      viewer.imageryLayers.addImageryProvider(
        new Cesium.UrlTemplateImageryProvider({
          url: 'https://webst01.is.autonavi.com/appmaptile?style=6&x={x}&y={y}&z={z}',
          credit: new Cesium.Credit('© 高德地图'),
          maximumLevel: 18,
        }),
      )

      // 隐藏不需要的 UI 元素
      viewer.scene.screenSpaceCameraController.enableCollisionDetection = false

      viewerRef.current = viewer

      // 绘制航线弧线和城市标签
      drawRoute(viewer)

      // 初始相机定位
      flyToOverview(viewer)

      return () => {
        if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
        viewer.destroy()
        viewerRef.current = null
      }
    } catch (e) {
      console.warn('[FlightMap3D] Cesium 初始化失败，可能缺少 WebGL 支持:', e)
    }
  }, [flight])

  // 绘制航线弧线和城市标注
  const drawRoute = useCallback(
    (viewer: Cesium.Viewer) => {
      // 清除旧实体
      viewer.entities.removeAll()
      cityLabelsRef.current = []

      const fromLng = flight.departureLng
      const fromLat = flight.departureLat
      const toLng = flight.arrivalLng
      const toLat = flight.arrivalLat

      // 生成弧线点
      const arcPoints = generateArcPoints(fromLng, fromLat, toLng, toLat, 100, 80000)

      // 绘制弧线
      arcEntityRef.current = viewer.entities.add({
        polyline: {
          positions: arcPoints,
          width: 3,
          material: new Cesium.PolylineGlowMaterialProperty({
            glowPower: 0.25,
            color: Cesium.Color.fromCssColorString('#438ede').withAlpha(0.8),
          }),
          clampToGround: false,
        },
      })

      // 出发城市标签
      const depLabel = viewer.entities.add({
        position: Cesium.Cartesian3.fromDegrees(fromLng, fromLat, 0),
        label: {
          text: `🛫 ${flight.departureCity}`,
          font: '14px sans-serif',
          fillColor: Cesium.Color.fromCssColorString('#438ede'),
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -10),
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
        point: {
          pixelSize: 10,
          color: Cesium.Color.fromCssColorString('#438ede'),
          outlineColor: Cesium.Color.WHITE,
          outlineWidth: 2,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      })
      cityLabelsRef.current.push(depLabel)

      // 到达城市标签
      const arrLabel = viewer.entities.add({
        position: Cesium.Cartesian3.fromDegrees(toLng, toLat, 0),
        label: {
          text: `🛬 ${flight.arrivalCity}`,
          font: '14px sans-serif',
          fillColor: Cesium.Color.fromCssColorString('#52c41a'),
          outlineColor: Cesium.Color.BLACK,
          outlineWidth: 2,
          style: Cesium.LabelStyle.FILL_AND_OUTLINE,
          verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
          pixelOffset: new Cesium.Cartesian2(0, -10),
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
        point: {
          pixelSize: 10,
          color: Cesium.Color.fromCssColorString('#52c41a'),
          outlineColor: Cesium.Color.WHITE,
          outlineWidth: 2,
          disableDepthTestDistance: Number.POSITIVE_INFINITY,
        },
      })
      cityLabelsRef.current.push(arrLabel)

      // 途经城市标签
      routeCities.forEach((city) => {
        const label = viewer.entities.add({
          position: Cesium.Cartesian3.fromDegrees(city.lng, city.lat, 0),
          label: {
            text: city.name,
            font: '12px sans-serif',
            fillColor: Cesium.Color.fromCssColorString('#f5a623'),
            outlineColor: Cesium.Color.BLACK,
            outlineWidth: 2,
            style: Cesium.LabelStyle.FILL_AND_OUTLINE,
            verticalOrigin: Cesium.VerticalOrigin.BOTTOM,
            pixelOffset: new Cesium.Cartesian2(0, -8),
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
            show: false, // 默认隐藏，飞机接近时显示
          },
          point: {
            pixelSize: 6,
            color: Cesium.Color.fromCssColorString('#f5a623'),
            outlineColor: Cesium.Color.WHITE,
            outlineWidth: 1,
            disableDepthTestDistance: Number.POSITIVE_INFINITY,
          },
        })
        cityLabelsRef.current.push(label)
      })

      // 飞机模型
      planeEntityRef.current = viewer.entities.add({
        position: Cesium.Cartesian3.fromDegrees(fromLng, fromLat, 0),
        orientation: new Cesium.ConstantProperty(Cesium.Matrix4.IDENTITY as any),
        model: {
          uri: `${PRODUCT_APP_BASE_URL}/models/plane.glb`,
          minimumPixelSize: 48,
          maximumScale: 200,
          color: Cesium.Color.WHITE,
        },
      })
    },
    [flight, routeCities],
  )

  // 飞到全局概览视角
  const flyToOverview = useCallback((viewer: Cesium.Viewer) => {
    const midLng = (flight.departureLng + flight.arrivalLng) / 2
    const midLat = (flight.departureLat + flight.arrivalLat) / 2
    viewer.camera.flyTo({
      destination: Cesium.Cartesian3.fromDegrees(midLng, midLat, 3000000),
      orientation: {
        heading: Cesium.Math.toRadians(0),
        pitch: Cesium.Math.toRadians(-60),
        roll: 0,
      },
      duration: 2,
    })
  }, [flight])

  // 飞到飞机后方跟随视角
  const flyToPlaneView = useCallback(
    (viewer: Cesium.Viewer) => {
      const pos = getPlanePositionAtProgress(
        flight.departureLng, flight.departureLat,
        flight.arrivalLng, flight.arrivalLat,
        progressRef.current,
        80000,
      )
      const heading = computeHeading(
        flight.departureLng, flight.departureLat,
        flight.arrivalLng, flight.arrivalLat,
      )
      // 相机位于飞机后方 500km，上方 100km
      viewer.camera.flyTo({
        destination: Cesium.Cartesian3.fromDegrees(
          pos.lng - Math.sin(heading) * 5,
          pos.lat - Math.cos(heading) * 5,
          pos.height + 200000,
        ),
        orientation: {
          heading: heading,
          pitch: Cesium.Math.toRadians(-25),
          roll: 0,
        },
        duration: 2,
      })
    },
    [flight],
  )

  // 视角模式切换
  useEffect(() => {
    if (!viewerRef.current) return
    if (viewMode === 'overview') {
      flyToOverview(viewerRef.current)
    } else {
      flyToPlaneView(viewerRef.current)
    }
  }, [viewMode, flyToOverview, flyToPlaneView])

  // 动画循环
  useEffect(() => {
    const viewer = viewerRef.current
    if (!viewer) return

    const animate = (timestamp: number) => {
      if (!lastTimeRef.current) lastTimeRef.current = timestamp
      const delta = timestamp - lastTimeRef.current
      lastTimeRef.current = timestamp

      if (isPlaying) {
        // 动画总时长映射：实际 duration 秒播放完（加速 60 倍）
        const totalMs = (flight.duration / 60) * 1000 / 60
        const progressDelta = delta / totalMs
        let newProgress = progressRef.current + progressDelta
        if (newProgress >= 1) {
          newProgress = 1
          onPlayToggle() // 播放完毕暂停
        }
        progressRef.current = newProgress
        onProgressChange(newProgress)
      }

      // 更新飞机位置
      updatePlanePosition()

      animFrameRef.current = requestAnimationFrame(animate)
    }

    animFrameRef.current = requestAnimationFrame(animate)

    return () => {
      if (animFrameRef.current) cancelAnimationFrame(animFrameRef.current)
    }
  }, [isPlaying, flight, onProgressChange, onPlayToggle])

  // 更新飞机位置和途经城市标签显隐
  const updatePlanePosition = useCallback(() => {
    const viewer = viewerRef.current
    const plane = planeEntityRef.current
    if (!viewer || !plane) return

    const progress = progressRef.current
    const pos = getPlanePositionAtProgress(
      flight.departureLng, flight.departureLat,
      flight.arrivalLng, flight.arrivalLat,
      progress,
      80000,
    )

    // 更新飞机位置
    plane.position = new Cesium.ConstantPositionProperty(
      Cesium.Cartesian3.fromDegrees(pos.lng, pos.lat, pos.height + 5000),
    )

    // 更新飞机朝向
    const heading = computeHeading(
      flight.departureLng, flight.departureLat,
      flight.arrivalLng, flight.arrivalLat,
    )
    const hpr = new Cesium.HeadingPitchRoll(heading, Cesium.Math.toRadians(5), 0)
    const orientation = Cesium.Transforms.headingPitchRollQuaternion(
      Cesium.Cartesian3.fromDegrees(pos.lng, pos.lat, pos.height + 5000),
      hpr,
    )
    plane.orientation = new Cesium.ConstantProperty(orientation)

    // 更新途经城市标签显隐（飞机接近时显示）
    const threshold = 3 // 3 度距离阈值
    cityLabelsRef.current.forEach((entity, idx) => {
      if (idx < 2) return // 跳过出发/到达城市（始终显示）
      const label = entity.label
      if (!label) return
      const entityPos = entity.position?.getValue(viewer.clock.currentTime) as Cesium.Cartesian3 | undefined
      if (!entityPos) return
      const cart = Cesium.Ellipsoid.WGS84.cartesianToCartographic(entityPos)
      const cityLng = Cesium.Math.toDegrees(cart.longitude)
      const cityLat = Cesium.Math.toDegrees(cart.latitude)
      const dist = Math.sqrt((pos.lng - cityLng) ** 2 + (pos.lat - cityLat) ** 2)
      ;(label.show as Cesium.ConstantProperty).setValue(dist < threshold)
    })

    // 飞机视角模式下跟随相机
    if (viewer.camera) {
      // 仅在飞机视角模式下跟随
      const heading2 = computeHeading(
        flight.departureLng, flight.departureLat,
        flight.arrivalLng, flight.arrivalLat,
      )
      // 判断当前是否为飞机跟随模式（简单通过 pitch 判断）
      const currentPitch = Cesium.Math.toDegrees(viewer.camera.pitch)
      if (currentPitch > -35 && currentPitch < -15) {
        // 大致在飞机后方视角范围内，执行跟随
        viewer.camera.lookAt(
          Cesium.Cartesian3.fromDegrees(pos.lng, pos.lat, pos.height + 5000),
          new Cesium.HeadingPitchRange(
            heading2 + Math.PI, // 看向飞机后方
            Cesium.Math.toRadians(-15),
            500000,
          ),
        )
      }
    }
  }, [flight])

  // 外部进度变化时（拖动进度条）同步更新
  useEffect(() => {
    progressRef.current = animProgress
    updatePlanePosition()
  }, [animProgress, updatePlanePosition])

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', position: 'relative' }}
    >
      <style>{`
        .cesium-viewer .cesium-widget-credits,
        .cesium-viewer .cesium-credit-logoContainer,
        .cesium-viewer .cesium-credit-expand-link {
          display: none !important;
        }
      `}</style>
    </div>
  )
}

export default FlightMap3D
