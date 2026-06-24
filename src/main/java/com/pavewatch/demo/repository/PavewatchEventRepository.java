package com.pavewatch.demo.repository;

import com.pavewatch.demo.model.PavewatchEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PavewatchEventRepository extends JpaRepository<PavewatchEvent, Long> {
}