package com.nuclei.userservice.entity;

import com.nuclei.userservice.util.EmailEncryptionUtil;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class UserEmailEncryptionListener {

    private final EmailEncryptionUtil emailEncryptionUtil;

    public UserEmailEncryptionListener(
            final EmailEncryptionUtil emailEncryptionUtil) {
        this.emailEncryptionUtil = emailEncryptionUtil;
    }

    @PrePersist
    @PreUpdate
    public void encryptEmail(final User user) {
        user.setEmail(
                emailEncryptionUtil.encrypt(
                        user.getEmail()
                )
        );
    }

    @PostLoad
    @PostPersist
    @PostUpdate
    public void decryptEmail(final User user) {
        user.setEmail(
                emailEncryptionUtil.decrypt(
                        user.getEmail()
                )
        );
    }
}