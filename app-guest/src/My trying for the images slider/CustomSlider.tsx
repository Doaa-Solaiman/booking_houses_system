
import React from 'react';
import { Swiper, SwiperSlide } from 'swiper/react';
import 'swiper/swiper-bundle.min.css';


import { Navigation, Pagination } from 'swiper';

const CustomSlider = ({ images }) => {
return (
	<Swiper
	modules={[Navigation, Pagination]}
	spaceBetween={50}
	slidesPerView={1}
	navigation
	pagination={{ clickable: true }}
	>
	{images.map((image, index) => (
		<SwiperSlide key={index}>
		<img src={(`../../die Bilder/${image}`)} alt={`Slide ${index}`} />
		</SwiperSlide>
	))}
	</Swiper>
);
};

export default CustomSlider;






{/*

import React, { useState, useEffect, useLayoutEffect } from 'react';

const CustomSlider = ({ children }) => {
const [activeIndex, setActiveIndex] = React.useState(0); // This is for keeping the track of the current active slide
const [slideDone, setSlideDone] = React.useState(true); // for indicating whether the current slide animation is complete
const [timeID, setTimeID] = React.useState(null); // to store the id of the timeout used for the automatic slide transition

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
	setActiveIndex((val) => (val >= React.Children.count(children) - 1 ? 0 : val + 1));
};

const slidePrev = () => {
	setActiveIndex((val) => (val <= 0 ? React.Children.count(children) - 1 : val - 1));
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
	{React.Children.map(children, (img, index) => (
		<div
		className={`slider__item slider__item-active-${activeIndex + 1}`}
		key={index}
		>
		{img}
		</div>
	))}

	<div className="container__slider__links">
		{React.Children.map(children, (_, index) => (
		<button
			key={index}
			className={
			activeIndex === index
				? 'container__slider__links-small container__slider__links-small-active'
				: 'container__slider__links-small'
			}
			onClick={(e) => {
			e.preventDefault();
			setActiveIndex(index);
			}}
		></button>
		))}
	</div>

	<button
		className="slider__btn-next"
		onClick={(e) => {
		e.preventDefault();
		slideNext();
		}}
	>
		{'>'}
	</button>
	<button
		className="slider__btn-prev"
		onClick={(e) => {
		e.preventDefault();
		slidePrev();
		}}
	>
		{'<'}
	</button>
	</div>
);
};

export default CustomSlider; */}
