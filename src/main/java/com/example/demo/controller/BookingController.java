package com.example.demo.controller;

import com.example.demo.model.Booking;
import com.example.demo.service.BookingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class BookingController {

    @Autowired
    private BookingService service;

    @Value("${server.id}")
    private String serverId;

    // ✅ BOOK (2PC)
    @PostMapping("/book")
    public String book(@RequestBody Booking b) {
        System.out.println("Nhận request /book: " + b.getName());
        service.book(b, serverId);
        return "Booking thành công!";
    }

    // ✅ PREPARE (Phase 1)
    @PostMapping("/prepare")
    public boolean prepare(@RequestBody Booking b) {
        return service.prepare(b);
    }

    // ✅ COMMIT (Phase 2)
    @PostMapping("/commit")
    public String commit(@RequestBody Booking b) {
        service.commit(b);
        return "COMMIT OK";
    }

    // ✅ LOG
    @GetMapping("/log")
    public List<String> logs() {
        return service.getLogs();
    }

    // ✅ STATUS
    @GetMapping("/status")
    public Object status() {
        return service.getServerStatus();
    }
}