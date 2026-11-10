package com.ecommerce.product.repository;

import com.ecommerce.product.entity.Flight;
import com.ecommerce.product.repository.mapper.FlightMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FlightRepository {

    private final FlightMapper flightMapper;

    public FlightRepository(FlightMapper flightMapper) {
        this.flightMapper = flightMapper;
    }

    public List<Flight> findAll() {
        return flightMapper.selectAll();
    }

    public Flight findById(Long id) {
        return flightMapper.selectById(id);
    }

    public List<Flight> search(String departureCity, String arrivalCity) {
        return flightMapper.search(departureCity, arrivalCity);
    }

    public List<Flight> findFrequencyData() {
        return flightMapper.selectFrequencyData();
    }

    public Flight save(Flight flight) {
        if (flight.getId() == null) {
            flightMapper.insert(flight);
        } else {
            flightMapper.updateById(flight);
        }
        return flight;
    }

    public void deleteById(Long id) {
        flightMapper.deleteById(id);
    }
}
