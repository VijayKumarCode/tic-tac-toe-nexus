package com.vk.gaming.nexus.repository;


import com.vk.gaming.nexus.model.GameMoveEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GameMoveRepository extends JpaRepository<GameMoveEntity, Long> {
}
