package com.codevam.vecindad.billing.application.port.out;

import com.codevam.vecindad.billing.domain.BillingSettings;

import java.util.UUID;

public interface BillingSettingsPort {
    BillingSettings load();
    void save(BillingSettings s, UUID by);
}
