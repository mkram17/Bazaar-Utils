package com.github.mkram17.bazaarutils.features.restrictsell;

import lombok.Getter;
import lombok.Setter;

public class SellItem {
    @Getter @Setter
    private int volume;
    @Getter @Setter
    private String name;

    public SellItem(int volume, String name) {
        this.volume = volume;
        this.name = name;
    }

}
