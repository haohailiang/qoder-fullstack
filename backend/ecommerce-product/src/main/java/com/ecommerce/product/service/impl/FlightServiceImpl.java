package com.ecommerce.product.service.impl;

import com.ecommerce.common.BusinessException;
import com.ecommerce.product.entity.Flight;
import com.ecommerce.product.repository.FlightRepository;
import com.ecommerce.product.service.FlightService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FlightServiceImpl implements FlightService {

    private static final Logger log = LoggerFactory.getLogger(FlightServiceImpl.class);

    private final FlightRepository flightRepository;

    public FlightServiceImpl(FlightRepository flightRepository) {
        this.flightRepository = flightRepository;
    }

    @Override
    public List<Flight> getAllFlights() {
        return flightRepository.findAll();
    }

    @Override
    public Flight getFlightById(Long id) {
        Flight flight = flightRepository.findById(id);
        if (flight == null) {
            throw new BusinessException(404, "航班不存在: " + id);
        }
        return flight;
    }

    @Override
    public List<Flight> searchFlights(String departureCity, String arrivalCity) {
        return flightRepository.search(departureCity, arrivalCity);
    }

    @Override
    public Map<String, Object> getFrequencyData() {
        List<Flight> flights = flightRepository.findFrequencyData();
        List<Map<String, Object>> cityFrequency = new ArrayList<>();

        for (Flight f : flights) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", f.getId());
            item.put("from", f.getDepartureCity());
            item.put("to", f.getArrivalCity());
            item.put("fromLng", f.getDepartureLng());
            item.put("fromLat", f.getDepartureLat());
            item.put("toLng", f.getArrivalLng());
            item.put("toLat", f.getArrivalLat());
            item.put("frequency", f.getFrequency());
            item.put("flightNumber", f.getFlightNumber());
            item.put("duration", f.getDuration());
            item.put("routeCoordinates", f.getRouteCoordinates());
            // 关联商品信息
            item.put("productId", f.getProductId());
            item.put("productName", f.getProductName());
            item.put("productPrice", f.getProductPrice());
            cityFrequency.add(item);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("flights", flights);
        result.put("cityFrequency", cityFrequency);
        return result;
    }

    @Override
    @Transactional
    public Flight createFlight(Flight flight) {
        flight.setCreatedDate(LocalDateTime.now());
        return flightRepository.save(flight);
    }

    @Override
    @Transactional
    public Flight updateFlight(Long id, Flight flight) {
        Flight existing = getFlightById(id);
        flight.setId(existing.getId());
        return flightRepository.save(flight);
    }

    @Override
    @Transactional
    public void deleteFlight(Long id) {
        getFlightById(id); // 确认存在
        flightRepository.deleteById(id);
    }
}
