import React, { useState, useEffect } from "react";

function CustomCarousel({ images }) {
const [activeIndex, setActiveIndex] = React.useState(0);
const [slideDone, setSlideDone] = React.useState(true);
const [timeID, setTimeID] = React.useState(null);

React.useEffect(() => {
	if (slideDone) {
	setSlideDone(false);
	setTimeID(
		setTimeout(() => {
		slideNext();
		setSlideDone(true);
		}, 5000)
	);
	}
}, [slideDone]);

const slideNext = () => {
	setActiveIndex((val) => {
	if (val >= images.length - 1) {
		return 0;
	} else {
		return val + 1;
	}
	});
};

const slidePrev = () => {
	setActiveIndex((val) => {
	if (val <= 0) {
		return images.length - 1;
	} else {
		return val - 1;
	}
	});
};

const AutoPlayStop = () => {
	if (timeID > 0) {
	clearTimeout(timeID);
	setSlideDone(false);
	}
};

const AutoPlayStart = () => {
	if (!slideDone) {
	setSlideDone(true);
	}
};

return (
	<div
	className="container__slider"
	onMouseEnter={AutoPlayStop}
	onMouseLeave={AutoPlayStart}
	>
	{images.map((image, index) => {
		return (
		<div
			className={"slider__item slider__item-active-" + (activeIndex + 1)}
			key={index}
		>
			<img className="arousel-image" src={image} alt={`Slide ${index}`} />
		</div>
		);
	})}

	<div className="container__slider__links">
		{images.map((item, index) => {
		return (
			<button
			key={index}
			className={
				activeIndex === index
				? "container__slider__links-small container__slider__links-small-active"
				: "container__slider__links-small"
			}
			onClick={(e) => {
				e.preventDefault();
				setActiveIndex(index);
			}}
			></button>
		);
		})}
	</div>

	<button
		className="slider__btn-next"
		onClick={(e) => {
		e.preventDefault();
		slideNext();
		}}
	>
		{">"}
	</button>
	<button
		className="slider__btn-prev"
		onClick={(e) => {
		e.preventDefault();
		slidePrev();
		}}
	>
		{"<"}
	</button>
	</div>
);
}

export default CustomCarousel;
