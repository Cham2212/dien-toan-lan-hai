package com.example.demo.service;

import com.example.demo.model.Booking;
import com.example.demo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate; // Đảm bảo đã import cái này

import java.util.ArrayList;
import java.util.List;

@Service
public class BookingService {

    @Autowired
    private BookingRepository repository;

    private List<String> logs = new ArrayList<>();

    // Danh sách server khác
    private String[] otherServers = {
            "https://hotel-booking-system-new.onrender.com",
            "https://dien-toan-lan-hai.onrender.com",

    };

    public void book(Booking b, String serverId) {

        repository.save(b);

        String logMsg = "Server " + serverId + " nhận booking: " + b.getName();
        logs.add(logMsg);

        if (!b.isReplicated()) {
            b.setReplicated(true);

            RestTemplate restTemplate = new RestTemplate();

            for (String url : otherServers) {
                try {
                    // replicate data
                    restTemplate.postForObject(url + "/api/replicate", b, String.class);

                    // replicate log
                    restTemplate.postForObject(url + "/api/replicate-log", logMsg, String.class);

                } catch (Exception e) {
                    logs.add("❌ Không gửi được tới " + url);
                }
            }
        }
    }

    public void replicate(Booking b, String serverId) {
        b.setReplicated(true);
        repository.save(b);
    }

    public List<String> getLogs() {
        return logs;
    }

    public void addLog(String log) {
        logs.add(log);
    }
}