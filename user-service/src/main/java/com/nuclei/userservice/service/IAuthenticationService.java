package com.nuclei.userservice.service;

public interface IAuthenticationService {

    String authenticate(String email, String password);
}
