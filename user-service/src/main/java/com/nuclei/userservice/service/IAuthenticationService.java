package com.nuclei.userservice.service;

@FunctionalInterface
public interface IAuthenticationService {

    String authenticate(String email, String password);
}
