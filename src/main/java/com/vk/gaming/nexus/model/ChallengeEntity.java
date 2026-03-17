/**
 * Problem No. #142
 * Difficulty: Medium
 * Description: Refactored ChallengeEntity to remove @Data antipattern
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1)
 * Space Complexity: O(1)
 */
package com.vk.gaming.nexus.model;

import com.vk.gaming.nexus.dto.ChallengeMessage;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "challenges")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChallengeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sender;
    private String receiver;
    private String roomId;

    @Enumerated(EnumType.STRING)
    private ChallengeMessage.ChallengeStatus status;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}