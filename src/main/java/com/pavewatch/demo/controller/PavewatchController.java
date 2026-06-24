package com.pavewatch.demo.controller;

import com.pavewatch.demo.model.PavewatchEvent;
import com.pavewatch.demo.repository.PavewatchEventRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pavewatchs")
@CrossOrigin(origins = "*")
public class PavewatchController {

    @Autowired
    private PavewatchEventRepository repository;

    @GetMapping
    public List<PavewatchEvent> getAllPavewatchs() {
        return repository.findAll();
    }
}