package com.nuclei.orderservice.service;

import com.nuclei.orderservice.dtos.CreateOrderRequestDto;
import com.nuclei.orderservice.dtos.GetOrderRequestDto;
import com.nuclei.orderservice.dtos.OrderResponseDto;

public interface OrderService {

    OrderResponseDto createOrder(CreateOrderRequestDto request);

    OrderResponseDto getOrder(Long userId,GetOrderRequestDto request);
}