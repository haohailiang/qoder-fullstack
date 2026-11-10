package com.ecommerce.product.repository.mapper;

import com.ecommerce.product.entity.Flight;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface FlightMapper {

    List<Flight> selectAll();

    Flight selectById(@Param("id") Long id);

    List<Flight> search(@Param("departureCity") String departureCity,
                        @Param("arrivalCity") String arrivalCity);

    /** 查询城市间航班频率统计（供飞线图使用） */
    List<Flight> selectFrequencyData();

    int insert(Flight flight);

    int updateById(Flight flight);

    int deleteById(@Param("id") Long id);
}
