package com.ecommerce.product.service;

import com.ecommerce.product.entity.Flight;

import java.util.List;
import java.util.Map;

public interface FlightService {

    List<Flight> getAllFlights();

    Flight getFlightById(Long id);

    List<Flight> searchFlights(String departureCity, String arrivalCity);

    /** 获取城市间航班频率统计数据（供飞线图使用） */
    Map<String, Object> getFrequencyData();

    Flight createFlight(Flight flight);

    Flight updateFlight(Long id, Flight flight);

    void deleteFlight(Long id);
}
