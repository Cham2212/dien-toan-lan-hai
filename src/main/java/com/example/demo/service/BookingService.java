package com.example.demo.service;

import com.example.demo.model.Booking;
import com.example.demo.repository.BookingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BookingService {

    @Autowired
    private BookingRepository repository;

    // 📝 Log
    private List<String> logs = new ArrayList<>();

    // 🌐 Server khác
    private String[] otherServers = {
            "https://hotel-booking-system-new.onrender.com",
            "https://dien-toan-lan-hai.onrender.com",
    };

    // 🧠 Trạng thái server
    private ConcurrentHashMap<String, Boolean> serverStatus = new ConcurrentHashMap<>();

    // 🕒 Lamport clock
    private int clock = 0;

    // ===== Constructor =====
    public BookingService() {
        for (String url : otherServers) {
            serverStatus.put(url, true);
        }
    }

    // ===== Lamport =====
    private synchronized int tick() {
        return ++clock;
    }

    private synchronized void updateClock(int received) {
        clock = Math.max(clock, received) + 1;
    }

    // ===== Log chuẩn =====
    private String log(String type, String message) {
        String time = LocalTime.now().withNano(0).toString();
        return "[" + time + "] [L=" + clock + "] [" + type + "] " + message;
    }

    // ================== BOOK (2PC) ==================
    public void book(Booking b, String serverId) {

        tick();
        logs.add(log("CLIENT", "Nhận request đặt phòng: " + b.getName()));

        // gán Lamport
        b.setLamportTime(clock);

        new Thread(() -> {

            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(2000);
            factory.setReadTimeout(2000);

            RestTemplate restTemplate = new RestTemplate(factory);

            List<String> okServers = new ArrayList<>();

            // ===== PHASE 1: PREPARE =====
            for (String url : otherServers) {

                try {
                    tick();
                    logs.add(log("2PC", "Gửi PREPARE tới " + url));

                    Boolean res = restTemplate.postForObject(
                            url + "/api/prepare",
                            b,
                            Boolean.class);

                    if (Boolean.TRUE.equals(res)) {
                        okServers.add(url);
                        serverStatus.put(url, true);
                        logs.add(log("2PC", "VOTE OK từ " + url));
                    } else {
                        logs.add(log("2PC", "VOTE FAIL từ " + url));
                    }

                } catch (Exception e) {
                    serverStatus.put(url, false);
                    logs.add(log("ERROR", "Server DOWN: " + url));
                }
            }

            // ===== PHASE 2: COMMIT =====
            if (okServers.size() == otherServers.length) {

                tick();
                logs.add(log("2PC", "TẤT CẢ OK → COMMIT"));

                // commit local
                repository.save(b);

                tick();
                logs.add(log("DATABASE", "Đã COMMIT local"));

                for (String url : okServers) {
                    try {
                        restTemplate.postForObject(
                                url + "/api/commit",
                                b,
                                String.class);
                    } catch (Exception e) {
                        logs.add(log("ERROR", "Commit fail: " + url));
                    }
                }

            } else {

                tick();
                logs.add(log("2PC", "ABORT do thiếu server"));
            }

        }).start();
    }

    // ================== PREPARE ==================
    public boolean prepare(Booking b) {

        updateClock(b.getLamportTime());

        logs.add(log("2PC", "Nhận PREPARE"));

        // luôn OK (có thể check logic)
        return true;
    }

    // ================== COMMIT ==================
    public void commit(Booking b) {

        updateClock(b.getLamportTime());

        logs.add(log("2PC", "Nhận COMMIT"));

        repository.save(b);

        tick();
        logs.add(log("DATABASE", "Đã COMMIT vào DB"));
    }

    // ================== LOG ==================
    public List<String> getLogs() {
        return logs;
    }

    // ================== STATUS ==================
    public ConcurrentHashMap<String, Boolean> getServerStatus() {
        return serverStatus;
    }
}