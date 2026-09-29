package com.nuclei.userservice.service;

public interface IIdempotencyService {

    String getResult(String idempotencyKey,
                     String requestHash);

    String saveResult(
            String idempotencyKey,
            String requestHash,
            String result);
}
