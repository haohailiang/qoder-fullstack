package com.ecommerce.product.service;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.entity.Flight;
import com.ecommerce.product.repository.FlightRepository;
import com.ecommerce.product.service.impl.FlightServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlightServiceImpl 单元测试：覆盖航班查询、城市筛选、频率统计聚合，
 * 以及新增/更新/删除的正常流程与"航班不存在"的异常分支。
 */
@ExtendWith(MockitoExtension.class)
class FlightServiceImplTest {

    @Mock
    private FlightRepository flightRepository;

    @InjectMocks
    private FlightServiceImpl flightService;

    private Flight sampleFlight;

    @BeforeEach
    void setUp() {
        sampleFlight = new Flight();
        sampleFlight.setId(1L);
        sampleFlight.setProductId(7L);
        sampleFlight.setDepartureCity("北京");
        sampleFlight.setArrivalCity("上海");
        sampleFlight.setDepartureLng(new BigDecimal("116.4074"));
        sampleFlight.setDepartureLat(new BigDecimal("39.9042"));
        sampleFlight.setArrivalLng(new BigDecimal("121.4737"));
        sampleFlight.setArrivalLat(new BigDecimal("31.2304"));
        sampleFlight.setFlightNumber("CA1234");
        sampleFlight.setDuration(135);
        sampleFlight.setFrequency(28);
        sampleFlight.setRouteCoordinates("[{\"name\":\"济南\",\"lng\":117.0,\"lat\":36.67}]");
        sampleFlight.setProductName("北京→上海 机票");
        sampleFlight.setProductPrice(new BigDecimal("1280.00"));
    }

    @Nested
    @DisplayName("getAllFlights - 查询全部航班")
    class GetAllFlights {

        @Test
        @DisplayName("正常场景：返回仓储列表")
        void shouldReturnAll() {
            when(flightRepository.findAll()).thenReturn(List.of(sampleFlight));

            List<Flight> result = flightService.getAllFlights();

            assertThat(result).hasSize(1);
            verify(flightRepository).findAll();
        }
    }

    @Nested
    @DisplayName("getFlightById - 按 ID 查询")
    class GetFlightById {

        @Test
        @DisplayName("正常场景：航班存在时返回")
        void shouldReturnWhenExists() {
            when(flightRepository.findById(1L)).thenReturn(sampleFlight);

            assertThat(flightService.getFlightById(1L)).isSameAs(sampleFlight);
        }

        @Test
        @DisplayName("异常场景：航班不存在时抛出 404 BusinessException")
        void shouldThrow404WhenMissing() {
            when(flightRepository.findById(99L)).thenReturn(null);

            assertThatThrownBy(() -> flightService.getFlightById(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("航班不存在")
                    .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
        }
    }

    @Nested
    @DisplayName("searchFlights - 城市筛选")
    class SearchFlights {

        @Test
        @DisplayName("正常场景：透传筛选条件到仓储")
        void shouldDelegateSearch() {
            when(flightRepository.search("北京", "上海")).thenReturn(List.of(sampleFlight));

            List<Flight> result = flightService.searchFlights("北京", "上海");

            assertThat(result).hasSize(1);
            verify(flightRepository).search("北京", "上海");
        }

        @Test
        @DisplayName("边界场景：无匹配时返回空列表")
        void shouldReturnEmpty() {
            when(flightRepository.search(null, null)).thenReturn(Collections.emptyList());

            assertThat(flightService.searchFlights(null, null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("getFrequencyData - 频率统计聚合")
    class GetFrequencyData {

        @Test
        @DisplayName("正常场景：返回 flights 与映射后的 cityFrequency")
        @SuppressWarnings("unchecked")
        void shouldBuildFrequencyMap() {
            when(flightRepository.findFrequencyData()).thenReturn(List.of(sampleFlight));

            Map<String, Object> result = flightService.getFrequencyData();

            assertThat(result).containsKeys("flights", "cityFrequency");
            assertThat((List<Flight>) result.get("flights")).hasSize(1);

            List<Map<String, Object>> cityFrequency =
                    (List<Map<String, Object>>) result.get("cityFrequency");
            assertThat(cityFrequency).hasSize(1);
            Map<String, Object> item = cityFrequency.get(0);
            assertThat(item)
                    .containsEntry("id", 1L)
                    .containsEntry("from", "北京")
                    .containsEntry("to", "上海")
                    .containsEntry("flightNumber", "CA1234")
                    .containsEntry("frequency", 28)
                    .containsEntry("duration", 135)
                    .containsEntry("productId", 7L)
                    .containsEntry("productName", "北京→上海 机票");
            assertThat(item).containsKeys("fromLng", "fromLat", "toLng", "toLat", "routeCoordinates");
        }

        @Test
        @DisplayName("边界场景：无航班时两个列表均为空")
        @SuppressWarnings("unchecked")
        void shouldReturnEmptyLists() {
            when(flightRepository.findFrequencyData()).thenReturn(Collections.emptyList());

            Map<String, Object> result = flightService.getFrequencyData();

            assertThat((List<?>) result.get("flights")).isEmpty();
            assertThat((List<?>) result.get("cityFrequency")).isEmpty();
        }
    }

    @Nested
    @DisplayName("createFlight - 新增航班")
    class CreateFlight {

        @Test
        @DisplayName("正常场景：写入创建时间并持久化")
        void shouldSetCreatedDateAndSave() {
            LocalDateTime before = LocalDateTime.now();

            ArgumentCaptor<Flight> captor = ArgumentCaptor.forClass(Flight.class);
            flightService.createFlight(sampleFlight);

            verify(flightRepository).save(captor.capture());
            // 服务层应在持久化前写入创建时间，并透传同一实体给仓储
            assertThat(captor.getValue()).isSameAs(sampleFlight);
            assertThat(captor.getValue().getCreatedDate()).isNotNull()
                    .isAfterOrEqualTo(before);
        }
    }

    @Nested
    @DisplayName("updateFlight - 更新航班")
    class UpdateFlight {

        @Test
        @DisplayName("正常场景：目标存在时用已存在主键覆盖后保存")
        void shouldUpdateWhenExists() {
            when(flightRepository.findById(1L)).thenReturn(sampleFlight);
            Flight incoming = new Flight();
            incoming.setId(null);
            incoming.setFlightNumber("MU9999");

            flightService.updateFlight(1L, incoming);

            // 服务层用已存在航班的主键覆盖入参后保存
            assertThat(incoming.getId()).isEqualTo(1L);
            verify(flightRepository).save(incoming);
        }

        @Test
        @DisplayName("异常场景：目标不存在时抛出 404 且不保存")
        void shouldThrow404AndNotSave() {
            when(flightRepository.findById(99L)).thenReturn(null);

            assertThatThrownBy(() -> flightService.updateFlight(99L, new Flight()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("航班不存在");
            verify(flightRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteFlight - 删除航班")
    class DeleteFlight {

        @Test
        @DisplayName("正常场景：存在时执行删除")
        void shouldDeleteWhenExists() {
            when(flightRepository.findById(1L)).thenReturn(sampleFlight);

            flightService.deleteFlight(1L);

            verify(flightRepository).deleteById(1L);
        }

        @Test
        @DisplayName("异常场景：不存在时抛出 404 且不执行删除")
        void shouldThrow404AndNotDelete() {
            when(flightRepository.findById(99L)).thenReturn(null);

            assertThatThrownBy(() -> flightService.deleteFlight(99L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("航班不存在");
            verify(flightRepository, never()).deleteById(any());
        }
    }
}
