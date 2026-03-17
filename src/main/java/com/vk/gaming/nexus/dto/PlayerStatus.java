package com.vk.gaming.nexus.dto;

/**
 * Problem No. #145
 * Difficulty: Easy
 * Description: Data Transfer Object for broadcasting player statuses
 * Link: https://github.com/VijayKumarCode/Nexus
 * Time Complexity: O(1)
 * Space Complexity: O(1)
 */

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PlayerStatus {
    private String username;
    private String status;
}
