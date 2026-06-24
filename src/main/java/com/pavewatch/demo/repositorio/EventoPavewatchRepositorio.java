package com.pavewatch.demo.repositorio;

import com.pavewatch.demo.modelo.EventoPavewatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventoPavewatchRepositorio extends JpaRepository<EventoPavewatch, Long> {
}