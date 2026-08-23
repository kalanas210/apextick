package com.apextick.booking.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Entity
@Table(name = "series")
@Getter
@Setter
@NoArgsConstructor
public class Series {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(name = "short_name")
    private String shortName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sport sport;

    private String tint;
    private String kicker;

    @Column(columnDefinition = "text")
    private String blurb;

    @Column(columnDefinition = "text")
    private String story;

    private String image;

    @Column(name = "mobile_hero_image")
    private String mobileHeroImage;

    @Column(nullable = false)
    private String currency;

    @Column(name = "currency_symbol")
    private String currencySymbol;

    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "text")
    private List<String> cities;

    private String scale;
}
