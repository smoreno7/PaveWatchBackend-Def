package com.pavewatch.demo.controller;

import com.pavewatch.demo.model.PotholeEvent;
import com.pavewatch.demo.repository.PotholeEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/potholes")
@CrossOrigin(origins = "*")
public class PotholeController {

    @Autowired
    private PotholeEventRepository repository;

    @GetMapping
    public List<PotholeEvent> getAllPotholes() {
        return repository.findAll();
    }
}