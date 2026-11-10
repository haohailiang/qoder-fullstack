package com.ecommerce.product.controller;

import com.ecommerce.common.BusinessException;
import com.ecommerce.common.GlobalExceptionHandler;
import com.ecommerce.product.entity.Flight;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FlightController 切片测试：使用 standalone MockMvc + GlobalExceptionHandler，
 * 验证 HTTP 路由、请求/响应序列化，以及业务异常到 Result.code 的映射。
 * 注意：GlobalExceptionHandler 未标注 @ResponseStatus，异常时 HTTP 状态恒为 200，
 * 真正的业务错误码体现在响应体 $.code 中。
 */
@ExtendWith(MockitoExtension.class)
class FlightControllerTest {

    @Mock
    private com.ecommerce.product.service.FlightService flightService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private Flight sampleFlight;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new FlightController(flightService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        sampleFlight = new Flight();
        sampleFlight.setId(1L);
        sampleFlight.setProductId(7L);
        sampleFlight.setDepartureCity("北京");
        sampleFlight.setArrivalCity("上海");
        sampleFlight.setFlightNumber("CA1234");
        sampleFlight.setDuration(135);
        sampleFlight.setFrequency(28);
        sampleFlight.setProductPrice(new BigDecimal("1280.00"));
    }

    @Nested
    @DisplayName("GET /flights - 查询全部航班")
    class GetAllFlights {

        @Test
        @DisplayName("正常场景：返回航班数组")
        void shouldReturnFlightList() throws Exception {
            when(flightService.getAllFlights()).thenReturn(List.of(sampleFlight));

            mockMvc.perform(get("/flights"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data[0].flightNumber").value("CA1234"));
        }
    }

    @Nested
    @DisplayName("GET /flights/{id} - 查询航班详情")
    class GetFlightById {

        @Test
        @DisplayName("正常场景：返回单个航班")
        void shouldReturnFlight() throws Exception {
            when(flightService.getFlightById(1L)).thenReturn(sampleFlight);

            mockMvc.perform(get("/flights/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.departureCity").value("北京"));
        }

        @Test
        @DisplayName("异常场景：航班不存在时 code=404")
        void shouldReturn404Code() throws Exception {
            when(flightService.getFlightById(99L))
                    .thenThrow(new BusinessException(404, "航班不存在: 99"));

            mockMvc.perform(get("/flights/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("航班不存在: 99"));
        }
    }

    @Nested
    @DisplayName("GET /flights/search - 城市筛选")
    class SearchFlights {

        @Test
        @DisplayName("正常场景：透传查询参数")
        void shouldPassQueryParams() throws Exception {
            when(flightService.searchFlights(eq("北京"), eq("上海")))
                    .thenReturn(List.of(sampleFlight));

            mockMvc.perform(get("/flights/search")
                            .param("departureCity", "北京")
                            .param("arrivalCity", "上海"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data[0].arrivalCity").value("上海"));
        }

        @Test
        @DisplayName("边界场景：无筛选参数时以 null 调用服务")
        void shouldHandleNoParams() throws Exception {
            when(flightService.searchFlights(null, null)).thenReturn(List.of());

            mockMvc.perform(get("/flights/search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("GET /flights/frequency - 频率统计")
    class GetFrequencyData {

        @Test
        @DisplayName("正常场景：返回 flights 与 cityFrequency 结构")
        void shouldReturnFrequency() throws Exception {
            Map<String, Object> cityItem = new LinkedHashMap<>();
            cityItem.put("id", 1L);
            cityItem.put("from", "北京");
            cityItem.put("to", "上海");
            cityItem.put("frequency", 28);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("flights", List.of(sampleFlight));
            data.put("cityFrequency", List.of(cityItem));

            when(flightService.getFrequencyData()).thenReturn(data);

            mockMvc.perform(get("/flights/frequency"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.cityFrequency[0].from").value("北京"))
                    .andExpect(jsonPath("$.data.cityFrequency[0].frequency").value(28));
        }
    }

    @Nested
    @DisplayName("POST /flights - 创建航班")
    class CreateFlight {

        @Test
        @DisplayName("正常场景：提交航班体并返回创建结果")
        void shouldCreateFlight() throws Exception {
            when(flightService.createFlight(any(Flight.class))).thenReturn(sampleFlight);

            mockMvc.perform(post("/flights")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleFlight)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.flightNumber").value("CA1234"));
        }
    }

    @Nested
    @DisplayName("PUT /flights/{id} - 更新航班")
    class UpdateFlight {

        @Test
        @DisplayName("正常场景：更新已存在航班")
        void shouldUpdateFlight() throws Exception {
            when(flightService.updateFlight(eq(1L), any(Flight.class))).thenReturn(sampleFlight);

            mockMvc.perform(put("/flights/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleFlight)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /flights/{id} - 删除航班")
    class DeleteFlight {

        @Test
        @DisplayName("正常场景：删除成功返回空数据")
        void shouldDeleteFlight() throws Exception {
            mockMvc.perform(delete("/flights/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }

        @Test
        @DisplayName("异常场景：航班不存在时 code=404")
        void shouldReturn404WhenMissing() throws Exception {
            doThrow(new BusinessException(404, "航班不存在: 99"))
                    .when(flightService).deleteFlight(99L);

            mockMvc.perform(delete("/flights/99"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404));
        }
    }
}
