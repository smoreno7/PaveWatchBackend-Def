package com.pavewatch.demo.controlador;

import com.pavewatch.demo.modelo.EventoPavewatch;
import com.pavewatch.demo.repositorio.EventoPavewatchRepositorio;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/pavewatchs")
@CrossOrigin(origins = "*")
public class PavewatchControlador {

    @Autowired
    private EventoPavewatchRepositorio repository;

    @GetMapping
    public List<EventoPavewatch> getAllPavewatchs() {
        return repository.findAll();
    }
}