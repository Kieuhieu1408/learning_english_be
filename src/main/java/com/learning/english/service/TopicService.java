package com.learning.english.service;

import com.learning.english.dto.TopicRequest;
import com.learning.english.model.Topic;
import com.learning.english.model.User;
import com.learning.english.repository.TopicRepository;
import com.learning.english.repository.UserRepository;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class TopicService {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    private User getCurrentUserEntity() {
        return userRepository.findById(getCurrentUserPrincipal().getId()).orElseThrow();
    }

    public List<Topic> getAllTopics() {
        return topicRepository.findAllByUserId(getCurrentUserPrincipal().getId());
    }

    @Transactional
    public Topic createTopic(TopicRequest request) {
        User user = getCurrentUserEntity();
        if (topicRepository.existsByNameAndUserId(request.getName(), user.getId())) {
            throw new IllegalArgumentException("Topic name already exists");
        }

        Topic topic = Topic.builder()
                .user(user)
                .name(request.getName())
                .build();
        return topicRepository.save(topic);
    }

    @Transactional
    public void deleteTopic(UUID id) {
        UUID userId = getCurrentUserPrincipal().getId();
        Topic topic = topicRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found or access denied"));
        topicRepository.delete(topic);
    }
}
