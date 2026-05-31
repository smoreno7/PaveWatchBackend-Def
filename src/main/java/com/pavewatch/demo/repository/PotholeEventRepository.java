package com.pavewatch.demo.repository;

import com.pavewatch.demo.model.PotholeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PotholeEventRepository extends JpaRepository<PotholeEvent, Long> {
}