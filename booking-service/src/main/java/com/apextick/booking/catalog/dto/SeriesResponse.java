package com.apextick.booking.catalog.dto;

import com.apextick.booking.catalog.Series;

import java.util.List;

public record SeriesResponse(
        Long id, String slug, String name, String shortName, String sport,
        String tint, String kicker, String blurb, String story, String image,
        String mobileHeroImage, String currency, String currencySymbol,
        List<String> cities, String scale) {

    public static SeriesResponse from(Series s) {
        if (s == null) {
            return null;
        }
        return new SeriesResponse(
                s.getId(), s.getSlug(), s.getName(), s.getShortName(),
                s.getSport() == null ? null : s.getSport().code(),
                s.getTint(), s.getKicker(), s.getBlurb(), s.getStory(), s.getImage(),
                s.getMobileHeroImage(), s.getCurrency(), s.getCurrencySymbol(),
                s.getCities(), s.getScale());
    }
}
