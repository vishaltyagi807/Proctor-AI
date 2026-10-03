package dev.varshit.proctor.persistence.search;

public record PageParams(int page, int size, String sortBy, boolean descending) {

    private static final int MAX_SIZE = 100;

    public static PageParams of(int page, int size, String sortBy, String direction) {
        return new PageParams(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_SIZE),
                sortBy,
                !"asc".equalsIgnoreCase(direction)
        );
    }

    public int offset() {
        return page * size;
    }
}
