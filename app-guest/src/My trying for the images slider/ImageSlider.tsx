import React, { useState } from 'react';
//import { FaArrowLeft, FaArrowRight } from 'react-icons/fa';


const ImageSlider = ({ images }) => {
const [currentIndex, setCurrentIndex] = useState(0);

const nextSlide = () => {
	setCurrentIndex((prevIndex) => (prevIndex + 1) % images.length);
};

const prevSlide = () => {
	setCurrentIndex((prevIndex) => (prevIndex - 1 + images.length) % images.length);
};

return (
	<div className="image-slider">
	  <button onClick={prevSlide}>Previous</button>
	  <img src={`../../die Bilder/${images[currentIndex]}`} alt={`Slide ${currentIndex}`} />
	  <button onClick={nextSlide}>Next</button>
	</div>
);
};

export default ImageSlider;
