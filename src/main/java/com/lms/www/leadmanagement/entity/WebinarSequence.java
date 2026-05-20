package com.lms.www.leadmanagement.entity;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "webinar_sequence")
@Data
@IdClass(WebinarSequenceId.class)
public class WebinarSequence {
    @Id
    @Column(name = "date_key", length = 6)
    private String dateKey;

    @Id
    @Column(name = "id")
    private Integer id = 1;

    @Column(name = "last_counter")
    private Integer lastCounter = 0;
}
