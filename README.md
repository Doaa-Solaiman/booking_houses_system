# 🏠 Fewo Buchung

## Project Description

Fewo Buchung is a web-based accommodation booking system developed for managing holiday homes, apartments, and guest rooms.

The main goal of the project is to allow property owners (hosts) to manage and publish their rental units easily while giving customers an easy way to browse and book available accommodations.

The system was developed during my vocational training (Ausbildung) and was one of my main long-term projects, developed over approximately one to one and a half years.

The platform supports both frontend and backend processes, including booking management, accommodation administration, image management, and customer reservation handling.

## User Roles

### Host (Property Owner)

The host can:

* Register and verify the account via email confirmation
* Create and manage accommodations (houses, apartments, rooms)
* Define available units for each accommodation
* Set the maximum number of guests per unit
* Add accommodation details and facilities (Wi-Fi, private bathroom, washing machine, etc.)
* Upload multiple images for each property
* Add image descriptions
* Change image order
* Select a cover image for the homepage
* View booking details and reservation history
* Monitor occupied and available units

### Guest (Customer)

The guest can:

* Browse available accommodations
* View accommodation details and images
* Select booking dates
* Book one or multiple units at the same time
* Enter personal booking information without registration

## Booking Validation Features

The system includes booking validation to prevent invalid reservations, such as:

* Minimum booking period of 3 days
* Departure date cannot be before arrival date
* Arrival and departure cannot be on the same day
* Availability checks based on existing bookings

This helps reduce booking conflicts and improves reservation accuracy.

## Technologies Used

* Java (Backend)
* React JS (Frontend)
* TypeScript
* SQL
* CSS
* REST API
* Eclipse IDE

## Project Structure

* app-host: Host interface and accommodation management
* app-guest: Guest booking interface
* app-shared: Shared components and logic between host and guest
* Backend: Java backend services
* Database: SQL structure and booking management

## Important Note

Online payment functionality was intentionally not implemented in this project, as secure payment systems require advanced knowledge in data protection and payment security.

As an apprentice developer, the focus was placed on building a reliable booking and management system with strong validation and administration features.

## Screenshots

Project screenshots will be added soon.
