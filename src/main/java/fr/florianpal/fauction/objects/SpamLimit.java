package fr.florianpal.fauction.objects;

/**
 * Rate limit of a token bucket.
 *
 * @param burst     number of actions allowed in a row before the throttling starts
 * @param perSecond number of actions restored every second
 */
public record SpamLimit(double burst, double perSecond) {
}
