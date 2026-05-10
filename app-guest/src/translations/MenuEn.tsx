import React from 'react';

function MenuEn({ houses, onBookNow }) {
	let imgTrack = "../../die Bilder/";

	return (
		<div className="menu">
			{houses.map((house) => (
				<div key={house.id} className="card">
					<img src={imgTrack + house.img[0]} alt={house.houseTitle} className="card-img" />
					<h3 className="card-title">{house.houseTitle}</h3>
					<p className="card-location">{house.location}</p>
					<p className="card-description">{house.description}</p>
					<p>Max number of people: {house.guest}</p>
					<p className="card-price">{house.price} €/ per night</p>
					<button className="card-button" onClick={() => onBookNow(house)}>View Offer</button>
				</div>
			))}
		</div>
	);
}

export default MenuEn;

