package com.ticketbooking.config;

import com.ticketbooking.payment.RazorpayProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RazorpayProperties.class)
public class AppConfig {
}
