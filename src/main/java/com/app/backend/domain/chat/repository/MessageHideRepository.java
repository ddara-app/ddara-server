package com.app.backend.domain.chat.repository;

import com.app.backend.domain.chat.entity.MessageHide;
import com.app.backend.domain.chat.entity.MessageHideId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageHideRepository extends JpaRepository<MessageHide, MessageHideId> {
}
