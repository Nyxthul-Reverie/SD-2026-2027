package tcp01.demo;

import java.io.Serializable;
import tcp01.Place;

public class BrokenPerson implements Serializable {
    private static final long serialVersionUID = 2L;

    private String name;
    private int year;
    private Place place;

    public BrokenPerson(String name, Place place, int year) {
        this.name = name;
        this.place = place;
        this.year = year;
    }

    public String getName() {
        return name;
    }

    public Place getPlace() {
        return place;
    }

    public int getYear() {
        return year;
    }
}
