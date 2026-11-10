package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Flight;
import com.ecommerce.product.repository.mapper.FlightMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlightRepository 单元测试：验证仓储层对 MyBatis Mapper 的委托调用，
 * 重点覆盖 save() 方法根据主键是否存在选择 insert 或 updateById 的分支逻辑。
 */
@ExtendWith(MockitoExtension.class)
class FlightRepositoryTest {

    @Mock
    private FlightMapper flightMapper;

    @InjectMocks
    private FlightRepository flightRepository;

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
        sampleFlight.setRouteCoordinates("[{\"name\":\"济南\"}]");
    }

    @Nested
    @DisplayName("findAll - 查询全部航班")
    class FindAll {

        @Test
        @DisplayName("正常场景：委托 mapper.selectAll 并返回结果")
        void shouldDelegateToSelectAll() {
            when(flightMapper.selectAll()).thenReturn(List.of(sampleFlight));

            List<Flight> result = flightRepository.findAll();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getFlightNumber()).isEqualTo("CA1234");
            verify(flightMapper).selectAll();
        }

        @Test
        @DisplayName("边界场景：无数据时返回空列表")
        void shouldReturnEmptyList() {
            when(flightMapper.selectAll()).thenReturn(Collections.emptyList());

            assertThat(flightRepository.findAll()).isEmpty();
        }
    }

    @Nested
    @DisplayName("findById - 按 ID 查询")
    class FindById {

        @Test
        @DisplayName("正常场景：返回匹配的航班")
        void shouldReturnFlight() {
            when(flightMapper.selectById(1L)).thenReturn(sampleFlight);

            Flight result = flightRepository.findById(1L);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            verify(flightMapper).selectById(1L);
        }

        @Test
        @DisplayName("边界场景：ID 不存在时返回 null")
        void shouldReturnNullWhenNotFound() {
            when(flightMapper.selectById(999L)).thenReturn(null);

            assertThat(flightRepository.findById(999L)).isNull();
        }
    }

    @Nested
    @DisplayName("search - 城市筛选")
    class Search {

        @Test
        @DisplayName("正常场景：透传出发/落地城市参数")
        void shouldPassBothCities() {
            when(flightMapper.search("北京", "上海")).thenReturn(List.of(sampleFlight));

            List<Flight> result = flightRepository.search("北京", "上海");

            assertThat(result).hasSize(1);
            verify(flightMapper).search(eq("北京"), eq("上海"));
        }

        @Test
        @DisplayName("边界场景：筛选条件为空时传 null")
        void shouldPassNullConditions() {
            when(flightMapper.search(null, null)).thenReturn(Collections.emptyList());

            assertThat(flightRepository.search(null, null)).isEmpty();
            verify(flightMapper).search(isNull(), isNull());
        }
    }

    @Nested
    @DisplayName("findFrequencyData - 频率统计数据")
    class FindFrequencyData {

        @Test
        @DisplayName("正常场景：委托 mapper.selectFrequencyData")
        void shouldDelegateToSelectFrequencyData() {
            when(flightMapper.selectFrequencyData()).thenReturn(List.of(sampleFlight));

            assertThat(flightRepository.findFrequencyData()).hasSize(1);
            verify(flightMapper).selectFrequencyData();
        }
    }

    @Nested
    @DisplayName("save - 新增或更新分支")
    class Save {

        @Test
        @DisplayName("新增场景：主键为 null 时执行 insert 而非 update")
        void shouldInsertWhenIdIsNull() {
            Flight newFlight = new Flight();
            newFlight.setId(null);
            newFlight.setProductId(8L);

            Flight saved = flightRepository.save(newFlight);

            verify(flightMapper).insert(newFlight);
            verify(flightMapper, never()).updateById(newFlight);
            assertThat(saved).isSameAs(newFlight);
        }

        @Test
        @DisplayName("更新场景：主键存在时执行 updateById 而非 insert")
        void shouldUpdateWhenIdPresent() {
            Flight saved = flightRepository.save(sampleFlight);

            verify(flightMapper).updateById(sampleFlight);
            verify(flightMapper, never()).insert(sampleFlight);
            assertThat(saved).isSameAs(sampleFlight);
        }
    }

    @Nested
    @DisplayName("deleteById - 删除航班")
    class DeleteById {

        @Test
        @DisplayName("正常场景：委托 mapper.deleteById")
        void shouldDelegateToDelete() {
            flightRepository.deleteById(1L);

            verify(flightMapper).deleteById(1L);
        }
    }
}
