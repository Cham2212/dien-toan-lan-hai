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
        logs.add(log("CLIENT", "Nhận request: " + b.getName()));

        b.setLamportTime(clock);

        new Thread(() -> {

            RestTemplate restTemplate = createRestTemplate();

            List<String> okServers = new ArrayList<>();

            // ===== PHASE 1: PREPARE =====
            for (String url : otherServers) {
                try {
                    tick();
                    logs.add(log("2PC", "Gửi PREPARE -> " + url));

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
                    logs.add(log("ERROR", "PREPARE FAIL: " + url));
                }
            }

            // ===== PHASE 2 =====
            if (okServers.size() == otherServers.length) {

                tick();
                logs.add(log("2PC", "ALL OK → COMMIT"));

                // commit local
                repository.save(b);
                logs.add(log("DATABASE", "COMMIT LOCAL"));

                for (String url : okServers) {
                    commitWithRetry(restTemplate, url, b);
                }

            } else {

                tick();
                logs.add(log("2PC", "ABORT do thiếu server"));

                for (String url : okServers) {
                    sendAbort(restTemplate, url, b);
                }
            }

        }).start();
    }

    // ================== PREPARE ==================
    public boolean prepare(Booking b) {

        updateClock(b.getLamportTime());
        logs.add(log("2PC", "Nhận PREPARE"));

        return true; // luôn OK
    }

    // ================== COMMIT ==================
    public String commit(Booking b) {

        updateClock(b.getLamportTime());

        logs.add(log("2PC", "Nhận COMMIT"));

        repository.save(b);

        tick();
        logs.add(log("DATABASE", "COMMIT DB"));

        return "OK"; // QUAN TRỌNG
    }

    // ================== ABORT ==================
    public String abort(Booking b) {

        updateClock(b.getLamportTime());

        logs.add(log("2PC", "Nhận ABORT"));

        return "ABORTED";
    }

    // ================== RETRY COMMIT ==================
    private void commitWithRetry(RestTemplate restTemplate, String url, Booking b) {

        int retry = 0;

        while (retry < 3) {
            try {
                String res = restTemplate.postForObject(
                        url + "/api/commit",
                        b,
                        String.class);

                logs.add(log("2PC", "COMMIT OK từ " + url));
                return;

            } catch (Exception e) {
                retry++;
                logs.add(log("ERROR", "Retry " + retry + " COMMIT fail: " + url));
            }
        }

        logs.add(log("ERROR", "COMMIT FAIL hoàn toàn: " + url));
    }

    // ================== ABORT SEND ==================
    private void sendAbort(RestTemplate restTemplate, String url, Booking b) {
        try {
            restTemplate.postForObject(url + "/api/abort", b, String.class);
            logs.add(log("2PC", "Gửi ABORT -> " + url));
        } catch (Exception e) {
            logs.add(log("ERROR", "ABORT FAIL: " + url));
        }
    }

    // ================== REST TEMPLATE ==================
    private RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
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