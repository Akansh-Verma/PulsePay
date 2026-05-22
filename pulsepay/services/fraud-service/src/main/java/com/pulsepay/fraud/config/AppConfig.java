package com.pulsepay.fraud.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Value("${fraud.detection.threshold:10000.0}")
    private double fraudThreshold;

    public double getFraudThreshold() {
        return fraudThreshold;
    }
}
