package com.kryptosystems.ballastasera.models.mappers;

import com.kryptosystems.ballastasera.enums.FeedNewsType;
import com.kryptosystems.ballastasera.enums.SocialRelation;
import com.kryptosystems.ballastasera.models.dtos.FeedActivity;
import com.kryptosystems.ballastasera.models.dtos.FeedDto;
import com.kryptosystems.ballastasera.models.dtos.FeedNewsDto;
import com.kryptosystems.ballastasera.models.dtos.FeedTonightDto;
import com.kryptosystems.ballastasera.models.dtos.SuggestionDto;
import com.kryptosystems.ballastasera.models.dtos.SuggestionsActivity;
import com.kryptosystems.ballastasera.models.dtos.UserSummaryDto;
import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import com.kryptosystems.ballastasera.models.entities.Events;
import com.kryptosystems.ballastasera.models.entities.UserFollows;
import com.kryptosystems.ballastasera.models.entities.Users;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Pestaña Amici: FeedActivity → FeedDto y SuggestionsActivity → SuggestionDto. user y event de cada fila llegan ya
 * cargados por los JOIN FETCH de los repositorios (el venue tambien, en las de esta noche).
 */
@Mapper(componentModel = "spring")
public interface FeedMapper {

    /** Igual que UserMapper.toSummaryDto: los default de aqui no pueden llamar a un metodo de otro mapper. */
    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "profilePublic", source = "user.showProfilePublic")
    UserSummaryDto toSummaryDto(Users user, SocialRelation relation);

    default FeedDto toFeedDto(FeedActivity activity) {
        FeedDto dto = new FeedDto();
        dto.setTonight(toTonightDtos(activity));
        dto.setNews(toNewsDtos(activity));
        return dto;
    }

    /** Las sugerencias en el orden en que llegan. relation: FOLLOWED_BY si ya sigue a quien mira, si no NONE. */
    default List<SuggestionDto> toSuggestionDtos(SuggestionsActivity activity) {
        return activity.users().stream().map(user -> {
            SuggestionDto dto = new SuggestionDto();
            dto.setUser(toSummaryDto(user, SocialRelation.of(false, activity.followerIds().contains(user.getId()))));
            dto.setMutualFriendsCount(activity.mutualFriendsCounts().getOrDefault(user.getId(), 0L));
            EventAttendance shared = activity.sharedEvents().get(user.getId());
            if (shared != null) {
                Events event = shared.getEvent();
                dto.setEventId(event.getId());
                dto.setEventSlug(event.getSlug());
                dto.setEventTitle(event.getTitle());
                dto.setEventStartAt(event.getStartAt());
                dto.setLookingForPartner(shared.isLookingForPartner());
            }
            return dto;
        }).toList();
    }

    /** Una fila por serata, en el orden de la query (hora de inicio); dentro, la gente en el orden de su «Ci vado».
     * Quien mira sigue a todos: relation FOLLOWING o MUTUAL. */
    private List<FeedTonightDto> toTonightDtos(FeedActivity activity) {
        Map<UUID, FeedTonightDto> byEvent = new LinkedHashMap<>();
        for (EventAttendance attendance : activity.tonight()) {
            Events event = attendance.getEvent();
            FeedTonightDto row = byEvent.computeIfAbsent(event.getId(), eventId -> {
                FeedTonightDto tonight = new FeedTonightDto();
                tonight.setEventId(eventId);
                tonight.setEventSlug(event.getSlug());
                tonight.setEventTitle(event.getTitle());
                tonight.setEventStartAt(event.getStartAt());
                tonight.setVenueName(event.getVenue() != null ? event.getVenue().getName() : null);
                tonight.setMeGoing(activity.myEventIds().contains(eventId));
                tonight.setPeople(new ArrayList<>());
                return tonight;
            });
            Users person = attendance.getUser();
            row.getPeople().add(toSummaryDto(person, SocialRelation.of(true, activity.friendIds().contains(person.getId()))));
        }
        return new ArrayList<>(byEvent.values());
    }

    /** «ti segue» y «va a X» juntos, los mas recientes primero. */
    private List<FeedNewsDto> toNewsDtos(FeedActivity activity) {
        Stream<FeedNewsDto> followers = activity.newFollowers().stream().map(follow -> toFollowedYou(follow, activity));
        Stream<FeedNewsDto> going = activity.goingSoon().stream().map(attendance -> toGoingTo(attendance, activity));
        return Stream.concat(followers, going)
                .sorted(Comparator.comparing(FeedNewsDto::getAt).reversed())
                .toList();
    }

    /** Lo sigue: relation MUTUAL si quien mira lo sigue tambien, si no FOLLOWED_BY («Ricambia»). */
    private FeedNewsDto toFollowedYou(UserFollows follow, FeedActivity activity) {
        Users follower = follow.getFollower();
        FeedNewsDto news = new FeedNewsDto();
        news.setType(FeedNewsType.FOLLOWED_YOU);
        news.setAt(follow.getCreatedAt());
        news.setUser(toSummaryDto(follower, SocialRelation.of(activity.friendIds().contains(follower.getId()), true)));
        return news;
    }

    private FeedNewsDto toGoingTo(EventAttendance attendance, FeedActivity activity) {
        Users person = attendance.getUser();
        Events event = attendance.getEvent();
        FeedNewsDto news = new FeedNewsDto();
        news.setType(FeedNewsType.GOING_TO);
        news.setAt(attendance.getCreatedAt());
        news.setUser(toSummaryDto(person, SocialRelation.of(true, activity.friendIds().contains(person.getId()))));
        news.setEventId(event.getId());
        news.setEventSlug(event.getSlug());
        news.setEventTitle(event.getTitle());
        news.setEventStartAt(event.getStartAt());
        news.setMeGoing(activity.myEventIds().contains(event.getId()));
        return news;
    }
}
