package com.nuclei.userservice.entity;

import com.nuclei.userservice.config.SpringContextHolder;
import com.nuclei.userservice.util.EmailEncryptionUtil;
import com.nuclei.userservice.util.PasswordUtil;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

public class UserEmailEncryptionListener {

    @PrePersist
    public void encryptEmail(final User user) {

        final EmailEncryptionUtil emailEncryptionUtil =
                SpringContextHolder.getBean(EmailEncryptionUtil.class);

        final PasswordUtil passwordUtil =
                SpringContextHolder.getBean(PasswordUtil.class);

        user.setEmail(
                emailEncryptionUtil.encrypt(
                        user.getEmail()
                )
        );

        user.setPasswordHash(
                passwordUtil.hash(
                        user.getRawPassword()
                )
        );

        user.setRawPassword(null);
    }

    @PreUpdate
    public void preUpdate(final User user) {

        final EmailEncryptionUtil emailEncryptionUtil =
                SpringContextHolder.getBean(EmailEncryptionUtil.class);

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

        final EmailEncryptionUtil emailEncryptionUtil =
                SpringContextHolder.getBean(EmailEncryptionUtil.class);

        user.setEmail(
                emailEncryptionUtil.decrypt(
                        user.getEmail()
                )
        );
    }
}