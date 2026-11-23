package com.kryptosystems.ballastasera.services.implementations;

import com.kryptosystems.ballastasera.exceptions.SocialActionException;
import com.kryptosystems.ballastasera.models.entities.UserBlocks;
import com.kryptosystems.ballastasera.models.entities.Users;
import com.kryptosystems.ballastasera.models.entities.keys.UserBlockId;
import com.kryptosystems.ballastasera.repositories.UserBlocksRepository;
import com.kryptosystems.ballastasera.repositories.UserFollowsRepository;
import com.kryptosystems.ballastasera.repositories.UsersRepository;
import com.kryptosystems.ballastasera.services.manager.BlocksService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlocksServiceImpl implements BlocksService {

    private final UserBlocksRepository userBlocksRepository;
    private final UserFollowsRepository userFollowsRepository;
    private final UsersRepository usersRepository;

    /** Se puede bloquear a un perfil privado o baneado (ej. alguien privado que te sigue). */
    @Override
    @Transactional
    public void block(UUID blockerId, UUID blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new SocialActionException(SocialActionException.SELF_ACTION, "You cannot block yourself");
        }
        Users blocked = usersRepository.findById(blockedId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new EntityNotFoundException("User not found with id " + blockedId));

        UserBlockId id = UserBlockId.of(blockerId, blockedId);
        if (!userBlocksRepository.existsById(id)) {
            UserBlocks block = new UserBlocks();
            block.setId(id);
            block.setBlocker(usersRepository.getReferenceById(blockerId));
            block.setBlocked(blocked);
            userBlocksRepository.save(block);
        }
        userFollowsRepository.deleteBetween(blockerId, blockedId);
    }

    @Override
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        userBlocksRepository.deleteById(UserBlockId.of(blockerId, blockedId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Users> findBlocked(UUID blockerId) {
        return userBlocksRepository.findByBlockerIdWithBlocked(blockerId).stream()
                .map(UserBlocks::getBlocked)
                .toList();
    }
}
