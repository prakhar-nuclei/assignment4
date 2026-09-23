package com.nuclei.userservice.service;

public interface IIdempotencyService {

    String getResult(String idempotencyKey);

    boolean saveResult(
            String idempotencyKey,
            String result);
}
