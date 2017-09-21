
function ScheduledRoom(id, number, type, startDate, endDate, allDay, hashData){
    this.id = id;
    this.number = number;
    this.type = type;
    // start date & time
    this.startDate = startDate;
    // end date and time
    this.endDate = endDate;
    // by default we show the start time on the agenda div element. If allDayEvent is set to true we won't show the time.
    this.allDay = allDay;

    // using jshashset.js library
    // an agenda item can store arbitrary data. Any key/value data can store in this hashtable
    this.agendaData = hashData;
}