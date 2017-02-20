# HexTimeWatch
A watchface for android wear that shows you the time of day subdivided by sixteen.

## Why

You are asking the wrong question. The real question is "it should have been this way from the start. Splitting the day into 24's and 60's makes no sense"

Because I'm curious and I like thinking about redesigning systems we use every day to make our lives better (although really its mostly just curiousity. I don't expect to change the world, just to ask questions about it.)

### How does it work

Each hand represents one sixteenth of the one above it. Its in sixteenths because 16 is a power of two, and those have a lot of benefits.

I just call these subdivisions h1 h2 h3 h4 h5, or firsts seconds thirds fourths fifths, meaning the first division into 16, the second division, etc.

It's roughly like this:

1048576 5ths per day
1 h5 = 0.082 sec = 82.397 ms

65536 4ths per day
1 h4 = 1.318 sec

4096 3rds per day
1 h3 = 21.094 sec

256 2nds
1 h2 = 337.5 sec = ~5.6 minutes

16 1sts
1 h1 = 5400s = 90 minutes

Also, while I was changing things up, I made the "0" of the clock on the bottom, so at midnight all the hands will point straight down instead of straight up. I thought this was cool because now the h1 hand roughly points to where the sun is.
