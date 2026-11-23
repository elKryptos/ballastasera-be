package com.kryptosystems.ballastasera.models.dtos;

import com.kryptosystems.ballastasera.models.entities.EventAttendance;
import org.springframework.data.domain.Page;

import java.util.Set;
import java.util.UUID;

/**
 * Una pagina de «Chi ci va» vista por quien mira. No se devuelve tal cual: AttendeeMapper la copia en
 * Page<AttendeeDto>, como FeedActivity en FeedDto.
 * <p>
 * attendees: los «Ci vado» de la pagina, con la persona ya cargada (JOIN FETCH).
 * followedIds: de esas personas, a quienes sigue quien mira. followerIds: quienes lo siguen a el.
 * Sin login los dos son null y relation queda null.
 */
public record AttendeesActivity(Page<EventAttendance> attendees, Set<UUID> followedIds, Set<UUID> followerIds) {
}
