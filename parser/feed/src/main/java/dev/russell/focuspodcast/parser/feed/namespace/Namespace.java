package dev.russell.focuspodcast.parser.feed.namespace;

import dev.russell.focuspodcast.parser.feed.HandlerState;
import dev.russell.focuspodcast.parser.feed.element.SyndElement;
import org.xml.sax.Attributes;

public abstract class Namespace {
    /** Called by a Feedhandler when in startElement and it detects a namespace element
     *     @return The SyndElement to push onto the stack
     * */
    public abstract SyndElement handleElementStart(String localName, HandlerState state, Attributes attributes);
    
    /** Called by a Feedhandler when in endElement and it detects a namespace element 
     * */
    public abstract void handleElementEnd(String localName, HandlerState state);
}
