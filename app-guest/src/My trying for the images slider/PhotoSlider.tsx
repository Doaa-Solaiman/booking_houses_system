import React from 'react';
import { Swiper, SwiperSlide } from 'swiper/react';
import 'swiper/swiper-bundle.min.css';
import 'swiper/swiper.min.css';
import SwiperCore, { Navigation, Pagination, Autoplay } from 'swiper/core';


SwiperCore.use([Navigation, Pagination, Autoplay]);

const CustomSlider = ({ images }) => {
return (
	<Swiper
	  navigation
	  pagination={{ clickable: true }}
	  autoplay={{ delay: 5000, disableOnInteraction: false }}
	  loop={true}
	  className="mySwiper"
	>
	  {images.map((image, index) => (
		<SwiperSlide key={index}>
		  <img src={image} alt={`Slide ${index}`} style={{ width: '100%' }} />
		</SwiperSlide>
	  ))}
	</Swiper>
);
};

export default CustomSlider;


