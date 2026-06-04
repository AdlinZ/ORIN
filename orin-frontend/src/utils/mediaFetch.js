export const fetchMediaArrayBuffer = async (url) => {
  const response = await fetch(url);
  if (!response.ok) throw new Error('Network response was not ok');
  return response.arrayBuffer();
};

export const fetchMediaBlob = async (url) => {
  const response = await fetch(url);
  if (!response.ok) throw new Error('Network response was not ok');
  return response.blob();
};
