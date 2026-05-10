// src/components/PhotoSlider.tsx
import React from 'react';
import Slider from 'react-slick';
import "slick-carousel/slick/slick.css";
import "slick-carousel/slick/slick-theme.css";

const PhotoSlider = ({ images }) => {
  const settings = {
    dots: true,
    infinite: true,
    speed: 500,
    slidesToShow: 1,
    slidesToScroll: 1,
    autoplay: true,
    autoplaySpeed: 5000,
    arrows: true,
  };

  return (
    <div className="photo-slider">
      <Slider {...settings}>
        {images.map((image, index) => (
          <div key={index}>
            <img src={`../../die Bilder/${image}`} alt={`Slide ${index}`} className="slider-image" />
          </div>
        ))}
      </Slider>
    </div>
  );
};

export default PhotoSlider;

