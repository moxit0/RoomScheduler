
function ScheduledItem(calendarId, title, scheduledRoom, backgroundColor, foregroundColor) {

    // a unique calendar ID to identify this agenda item. 
    this.calendarId = calendarId;

    // agenda title to be displayed on div element
    this.title = title;
    this.scheduledRoom = scheduledRoom;
    // By default we use the colors defined in the CSS file but we can override those settings
    // if we set these variables.
    this.backgroundColor = backgroundColor;
    this.foregroundColor = foregroundColor;
}
