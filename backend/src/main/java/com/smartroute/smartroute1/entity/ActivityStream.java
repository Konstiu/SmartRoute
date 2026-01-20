package com.smartroute.smartroute1.entity;

import com.smartroute.smartroute1.entity.enums.ActivityStreamSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class ActivityStream {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Lob
    @Column(name = "time_stream")
    private byte[] timeStream;

    @Lob
    @Column(name = "distance_stream")
    private byte[] distanceStream;

    @Lob
    @Column(name = "heartrate_stream")
    private byte[] heartrateStream;

    @Enumerated(EnumType.STRING)
    private ActivityStreamSource source;
}
