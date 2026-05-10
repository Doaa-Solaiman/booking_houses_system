import React from 'react';

function Menu({ houses, onBookNow }) {
	let imgTrack = "../../die Bilder/";
	return (
		<div className="menu">
			{houses.map((house) => (
				<div key={house.id} className="card">
					<img src={imgTrack + house.img} alt={house.houseTitle} className="card-img" />
					{/*<img src={imgTrack + house.img} alt={house.houseTitle} className="card-img" />*/}
					<h3 className="card-title">{house.houseTitle}</h3>
					<p className="card-location">{house.location}</p>
					<p className="card-description">{house.description}</p>
					<p className="card-price">€{house.price} / pro Nacht</p>
					<button className="card-button" onClick={() => onBookNow(house)}>zum Angebot</button>
				</div>
			))}
		</div>
	);
}

export default Menu;
