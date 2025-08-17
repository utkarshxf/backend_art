import os
import requests
import json
import time
import logging
import hashlib
from bs4 import BeautifulSoup
from urllib.parse import urljoin
import re
import pickle
import signal
import sys

# Set up logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler("artwork_import.log"),
        logging.StreamHandler()
    ]
)


class ArtworkImporter:
    def __init__(self, base_url="http://localhost:7040", delay=1, checkpoint_file="artwork_checkpoint.pkl"):
        """
        Initialize the artwork importer.

        Args:
            base_url (str): Base URL of the API
            delay (int): Delay between requests in seconds
            checkpoint_file (str): File to store checkpoint data
        """
        self.base_url = base_url
        self.delay = delay
        self.wikipedia_base_url = "https://en.wikipedia.org"
        self.checkpoint_file = checkpoint_file
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'ArtResearchBot/1.0 (educational purposes)',
            'Content-Type': 'application/json',
            'accept': '*/*'
        })

        # Cache for existing artists and genres to avoid duplicates
        self.artist_cache = set()
        self.genre_cache = set()
        self.artwork_cache = set()

        # Checkpoint data
        self.processed_urls = set()
        self.queue = []
        self.current_sources = []

        # Load checkpoint if exists
        self.load_checkpoint()

        # Load existing data from API
        self.load_existing_data()

        # Setup signal handler for graceful exit
        signal.signal(signal.SIGINT, self.handle_exit)
        signal.signal(signal.SIGTERM, self.handle_exit)

    def handle_exit(self, signum, frame):
        """Handle exit signals by saving checkpoint before exiting"""
        logging.info("Received exit signal. Saving checkpoint...")
        self.save_checkpoint()
        sys.exit(0)

    def is_artwork_page(self, soup):
        """
        Determine if a Wikipedia page is about an artwork.

        Args:
            soup (BeautifulSoup): Parsed HTML content

        Returns:
            bool: True if the page appears to be about an artwork
        """
        # Check infobox for artwork indicators
        infobox = soup.select_one('.infobox, .vcard')
        if infobox:
            # Check if the infobox has artwork-related fields
            artwork_fields = ['artist', 'year', 'medium', 'dimensions', 'location', 'type', 'created']
            rows = infobox.select('tr')
            for row in rows:
                header = row.select_one('th')
                if header:
                    header_text = header.get_text().strip().lower()
                    if any(field in header_text for field in artwork_fields):
                        return True

        # Check if the page title or categories suggest an artwork
        title_element = soup.select_one('h1#firstHeading')
        if title_element:
            title = title_element.get_text().lower()
            artwork_keywords = ['painting', 'portrait', 'sculpture', 'artwork', 'masterpiece']
            if any(keyword in title for keyword in artwork_keywords):
                return True

        # Check categories for artwork indicators
        categories = soup.select('div.mw-normal-catlinks ul li a')
        for category in categories:
            category_text = category.get_text().lower()
            if any(term in category_text for term in ['painting', 'artwork', 'sculpture', 'portrait', 'masterpiece']):
                return True

        return False

    def is_artist_page(self, soup):
        """
        Determine if a Wikipedia page is about an artist.

        Args:
            soup (BeautifulSoup): Parsed HTML content

        Returns:
            bool: True if the page appears to be about an artist
        """
        # Check infobox for artist indicators
        infobox = soup.select_one('.infobox, .vcard')
        if infobox:
            # Check if the infobox has artist-related fields
            artist_fields = ['born', 'nationality', 'known for', 'movement', 'works', 'notable work']
            rows = infobox.select('tr')
            for row in rows:
                header = row.select_one('th')
                if header:
                    header_text = header.get_text().strip().lower()
                    if any(field in header_text for field in artist_fields):
                        # Check if it mentions art-related terms in the infobox
                        values = row.select_one('td')
                        if values:
                            value_text = values.get_text().lower()
                            art_terms = ['artist', 'paint', 'sculpt', 'artwork', 'portrait', 'gallery', 'exhibition']
                            if any(term in value_text for term in art_terms):
                                return True

        # Check if the page title or categories suggest an artist
        title_element = soup.select_one('h1#firstHeading')
        if title_element:
            title = title_element.get_text().lower()
            if 'artist' in title or 'painter' in title or 'sculptor' in title:
                return True

        # Check categories for artist indicators
        categories = soup.select('div.mw-normal-catlinks ul li a')
        for category in categories:
            category_text = category.get_text().lower()
            if any(term in category_text for term in ['artist', 'painter', 'sculptor', 'renaissance artist']):
                return True

        return False

    def extract_related_links(self, soup):
        """
        Extract links that might be related to artworks or artists.

        Args:
            soup (BeautifulSoup): Parsed HTML content
        """
        # Look for links in the content area
        content_div = soup.select_one('#mw-content-text')
        if not content_div:
            return

        # Keywords that suggest the link might point to an artwork or artist
        art_keywords = ['painting', 'sculpture', 'portrait', 'artwork', 'artist', 'painter', 'sculptor']

        # Find related articles in the "See also" section
        see_also = soup.find('span', id='See_also')
        if see_also:
            see_also_section = see_also.find_parent('h2')
            if see_also_section:
                # Get the next UL after the "See also" heading
                next_ul = see_also_section.find_next('ul')
                if next_ul:
                    for li in next_ul.find_all('li'):
                        link = li.find('a')
                        if link and link.get('href', '').startswith('/wiki/'):
                            # Skip certain namespaces
                            if ':' in link.get('href') and not link.get('href').startswith('/wiki/Category:'):
                                continue
                            # Only add if the link text contains art-related keywords
                            link_text = link.get_text().lower()
                            if any(keyword in link_text for keyword in art_keywords):
                                full_url = urljoin(self.wikipedia_base_url, link['href'])
                                if full_url not in self.processed_urls and full_url not in self.queue:
                                    self.queue.append(full_url)

        # Be more selective with links from content area
        art_related_links = []
        for link in content_div.select('a[href^="/wiki/"]'):
            href = link.get('href')

            # Skip certain namespaces and special pages
            if ':' in href and not href.startswith('/wiki/Category:'):
                continue

            # Only add if the link text strongly suggests art-related content
            link_text = link.get_text().lower()
            if any(keyword in link_text for keyword in art_keywords):
                full_url = urljoin(self.wikipedia_base_url, link['href'])
                if full_url not in self.processed_urls and full_url not in self.queue and full_url not in art_related_links:
                    art_related_links.append(full_url)

        # Add only a limited number of links to avoid going off-topic
        for url in art_related_links[:5]:  # Limit to 5 links per page
            self.queue.append(url)

        # Find related categories - be more selective
        category_links = soup.select('div.mw-normal-catlinks ul li a')
        art_related_categories = []
        for link in category_links:
            if link.get('href', '').startswith('/wiki/Category:'):
                category_text = link.get_text().lower()
                art_category_keywords = ['painting', 'artwork', 'artist', 'painter', 'sculptor',
                                         'renaissance', 'baroque', 'impressionism', 'art movement']
                if any(keyword in category_text for keyword in art_category_keywords):
                    category_url = urljoin(self.wikipedia_base_url, link['href'])
                    art_related_categories.append(category_url)

        # Add only a limited number of categories
        for category_url in art_related_categories[:3]:  # Limit to 3 categories per page
            self.add_category_pages_to_queue(category_url, limit=5)
    def load_checkpoint(self):
        """Load checkpoint data if available"""
        if os.path.exists(self.checkpoint_file):
            try:
                with open(self.checkpoint_file, 'rb') as f:
                    checkpoint_data = pickle.load(f)
                    self.processed_urls = checkpoint_data.get('processed_urls', set())
                    self.queue = checkpoint_data.get('queue', [])
                    self.current_sources = checkpoint_data.get('current_sources', [])
                logging.info(
                    f"Loaded checkpoint: {len(self.processed_urls)} processed URLs, {len(self.queue)} URLs in queue")
            except Exception as e:
                logging.error(f"Error loading checkpoint: {str(e)}")
                # Initialize with empty data
                self.processed_urls = set()
                self.queue = []
                self.current_sources = []
        else:
            logging.info("No checkpoint file found. Starting fresh.")
            self.processed_urls = set()
            self.queue = []
            self.current_sources = []

    def save_checkpoint(self):
        """Save checkpoint data"""
        try:
            checkpoint_data = {
                'processed_urls': self.processed_urls,
                'queue': self.queue,
                'current_sources': self.current_sources
            }
            with open(self.checkpoint_file, 'wb') as f:
                pickle.dump(checkpoint_data, f)
            logging.info(
                f"Saved checkpoint: {len(self.processed_urls)} processed URLs, {len(self.queue)} URLs in queue")
        except Exception as e:
            logging.error(f"Error saving checkpoint: {str(e)}")

    def fetch_with_retry(self, url, max_retries=None):
        """
        Fetch a URL with retry logic for handling connection issues.

        Args:
            url (str): URL to fetch
            max_retries (int): Maximum number of retries (None for unlimited)

        Returns:
            requests.Response: Response object if successful

        Raises:
            Exception: If max retries exceeded or other error occurs
        """
        retries = 0

        while max_retries is None or retries <= max_retries:
            try:
                response = self.session.get(url)
                response.raise_for_status()
                return response
            except (requests.ConnectionError, requests.Timeout) as e:
                retries += 1
                if max_retries is not None and retries > max_retries:
                    logging.error(f"Max retries exceeded for {url}: {str(e)}")
                    raise

                logging.warning(f"Connection issue: {str(e)}. Retrying in 5 seconds...")
                time.sleep(5)
            except Exception as e:
                # For non-connection errors, raise immediately
                logging.error(f"Error fetching {url}: {str(e)}")
                raise

    def post_with_retry(self, url, data, max_retries=None):
        """
        Post data to a URL with retry logic for handling connection issues.

        Args:
            url (str): URL to post to
            data (str): JSON data to send
            max_retries (int): Maximum number of retries (None for unlimited)

        Returns:
            requests.Response: Response object if successful

        Raises:
            Exception: If max retries exceeded or other error occurs
        """
        retries = 0

        while max_retries is None or retries <= max_retries:
            try:
                response = self.session.post(url, data=data)
                response.raise_for_status()
                return response
            except (requests.ConnectionError, requests.Timeout) as e:
                retries += 1
                if max_retries is not None and retries > max_retries:
                    logging.error(f"Max retries exceeded for {url}: {str(e)}")
                    raise

                logging.warning(f"Connection issue: {str(e)}. Retrying in 5 seconds...")
                time.sleep(5)
            except Exception as e:
                # For non-connection errors, raise immediately
                logging.error(f"Error posting to {url}: {str(e)}")
                raise

    def load_existing_data(self):
        """Load existing artists, genres, and artworks to avoid duplicates"""
        try:
            # Load existing artists
            try:
                response = self.fetch_with_retry(f"{self.base_url}/artist")
                if response.status_code == 200:
                    artists = response.json()
                    for artist in artists:
                        self.artist_cache.add(artist.get('id'))
                        self.artist_cache.add(artist.get('name').lower())
            except Exception as e:
                logging.error(f"Error loading artists: {str(e)}")

            # Load existing genres
            try:
                response = self.fetch_with_retry(f"{self.base_url}/genres")
                if response.status_code == 200:
                    genres = response.json()
                    for genre in genres:
                        self.genre_cache.add(genre.get('id'))
                        self.genre_cache.add(genre.get('name').lower())
            except Exception as e:
                logging.error(f"Error loading genres: {str(e)}")

            # Load existing artworks
            try:
                response = self.fetch_with_retry(f"{self.base_url}/artwork")
                if response.status_code == 200:
                    artworks = response.json()
                    for artwork in artworks:
                        self.artwork_cache.add(artwork.get('title').lower())
                        # Add URL to processed list if available
                        if 'sourceUrl' in artwork and artwork['sourceUrl']:
                            self.processed_urls.add(artwork['sourceUrl'])
            except Exception as e:
                logging.error(f"Error loading artworks: {str(e)}")

            logging.info(
                f"Loaded {len(self.artist_cache) // 2} artists, {len(self.genre_cache) // 2} genres, and {len(self.artwork_cache)} artworks")
        except Exception as e:
            logging.error(f"Error loading existing data: {str(e)}")

    def generate_id(self, text):
        """Generate a unique ID based on text"""
        return hashlib.md5(text.encode()).hexdigest()[:8]

    def create_genre(self, name):
        """
        Create a genre if it doesn't exist.

        Args:
            name (str): Name of the genre

        Returns:
            str: ID of the genre, or None if creation fails
        """
        if not name:
            name = "Other"

        # Check if genre already exists
        if name.lower() in self.genre_cache:
            # Get id from API by name
            try:
                response = self.fetch_with_retry(f"{self.base_url}/genres")
                if response.status_code == 200:
                    genres = response.json()
                    for genre in genres:
                        if genre.get('name').lower() == name.lower():
                            return genre.get('id')
            except Exception as e:
                logging.error(f"Error retrieving genre {name}: {str(e)}")
                # If we can't find it, generate a consistent ID
                genre_id = self.generate_id(name)
                return genre_id

            # If we can't find it, generate a consistent ID
            genre_id = self.generate_id(name)
            return genre_id

        # Create a new genre
        genre_id = self.generate_id(name)
        genre_data = {
            "id": genre_id,
            "key": genre_id,
            "name": name
        }

        try:
            response = self.post_with_retry(
                f"{self.base_url}/genres",
                data=json.dumps(genre_data)
            )

            if response.status_code in [200, 201]:
                logging.info(f"Created genre: {name} with ID: {genre_id}")
                self.genre_cache.add(genre_id)
                self.genre_cache.add(name.lower())
                return genre_id
            else:
                logging.error(
                    f"Failed to create genre: {name}. Status: {response.status_code}, Response: {response.text}")
                # Return generated ID even on failure
                return genre_id
        except Exception as e:
            logging.error(f"Error creating genre {name}: {str(e)}")
            # Return generated ID even on failure
            return genre_id

    def create_artist(self, artist_data):
        """
        Create an artist if it doesn't exist.

        Args:
            artist_data (dict): Artist data

        Returns:
            str: ID of the artist
        """
        name = artist_data.get('name', '').strip()
        if not name:
            return None

        # Generate a consistent ID for the artist
        artist_id = self.generate_id(name)

        # Check if artist already exists
        if artist_id in self.artist_cache or name.lower() in self.artist_cache:
            return artist_id

        # Prepare artist data
        api_artist_data = {
            "id": artist_id,
            "name": name,
            "birth_date": artist_data.get('birth_date', ''),
            "death_date": artist_data.get('death_date', ''),
            "nationality": artist_data.get('nationality', ''),
            "notable_works": artist_data.get('notable_works', ''),
            "art_movement": artist_data.get('art_movement', ''),
            "education": artist_data.get('education', ''),
            "awards": artist_data.get('awards', ''),
            "image_url": artist_data.get('image_url', ''),
            "wikipedia_url": artist_data.get('wikipedia_url', ''),
            "description": artist_data.get('description', '')
        }

        try:
            response = self.post_with_retry(
                f"{self.base_url}/artist",
                data=json.dumps(api_artist_data)
            )

            if response.status_code in [200, 201]:
                logging.info(f"Created artist: {name} with ID: {artist_id}")
                self.artist_cache.add(artist_id)
                self.artist_cache.add(name.lower())
                return artist_id
            else:
                logging.error(
                    f"Failed to create artist: {name}. Status: {response.status_code}, Response: {response.text}")
                return None
        except Exception as e:
            logging.error(f"Error creating artist {name}: {str(e)}")
            return None

    def create_artwork(self, artist_id, artwork_data):
        """
        Create an artwork.

        Args:
            artist_id (str): ID of the artist
            artwork_data (dict): Artwork data

        Returns:
            bool: True if successful, False otherwise
        """
        title = artwork_data.get('title', '').strip()
        if not title or title.lower() in self.artwork_cache:
            return False

        # Create genre if not exists
        genre_name = artwork_data.get('art_movement') or artwork_data.get('periodStyle') or "Other"
        genre_id = self.create_genre(genre_name)

        # If genre creation fails, generate a consistent ID for it
        if not genre_id:
            logging.warning(f"Genre creation failed for {genre_name}, using generated ID")
            genre_id = self.generate_id(genre_name)

        # Determine art type
        art_type = "IMAGE"  # Default
        medium = artwork_data.get('medium', '').lower()
        if medium:
            if any(term in medium for term in ['sculpture', 'statue', '3d']):
                art_type = "SCULPTURE"
            elif any(term in medium for term in ['installation']):
                art_type = "INSTALLATION"

        # Format date
        released_date = None
        release_year = None
        year_str = artwork_data.get('year', '')
        if year_str:
            # Try to extract a year
            year_match = re.search(r'\b(1\d{3}|20\d{2})\b', year_str)
            if year_match:
                release_year = int(year_match.group(1))
                released_date = f"{release_year}-01-01T00:00:00.000Z"

        # Prepare artwork data
        api_artwork_data = {
            "title": title,
            "imageUrl": artwork_data.get('image_url', ''),
            "imageUrlCompressed": artwork_data.get('image_url_compressed', ''),
            "storageType": "Firebase",
            "medium": artwork_data.get('medium', ''),
            "artist": artwork_data.get('artist', ''),
            "artType": art_type,
            "galleryId": "",
            "genreId": genre_id,
            "description": artwork_data.get('description', ''),
            "releasedDate": released_date,
            "releaseYear": release_year,
            "dimensions": artwork_data.get('dimensions', ''),
            "currentLocation": artwork_data.get('current_location', ''),
            "periodStyle": artwork_data.get('period_style', ''),
            "artMovement": artwork_data.get('art_movement', ''),
            "licenseInfo": artwork_data.get('license_info', ''),
            "sourceUrl": artwork_data.get('source_url', '')
        }

        try:
            response = self.post_with_retry(
                f"{self.base_url}/artwork/artist/{artist_id}",
                data=json.dumps(api_artwork_data)
            )

            if response.status_code in [200, 201]:
                logging.info(f"Created artwork: {title} for artist ID: {artist_id}")
                self.artwork_cache.add(title.lower())
                # Add source URL to processed list
                if artwork_data.get('source_url'):
                    self.processed_urls.add(artwork_data.get('source_url'))
                return True
            else:
                logging.error(
                    f"Failed to create artwork: {title}. Status: {response.status_code}, Response: {response.text}")
                return False
        except Exception as e:
            logging.error(f"Error creating artwork {title}: {str(e)}")
            return False
    def extract_artist_details(self, artist_url):
        """
        Extract artist details from Wikipedia.

        Args:
            artist_url (str): URL of the artist's Wikipedia page

        Returns:
            dict: Artist details
        """
        logging.info(f"Extracting artist details from: {artist_url}")

        details = {
            'name': '',
            'birth_date': '',
            'death_date': '',
            'nationality': '',
            'notable_works': '',
            'art_movement': '',
            'education': '',
            'awards': '',
            'image_url': '',
            'wikipedia_url': artist_url,
            'description': ''
        }

        try:
            response = self.fetch_with_retry(artist_url)
            soup = BeautifulSoup(response.text, 'html.parser')

            # Extract name from title
            title_element = soup.select_one('h1#firstHeading')
            if title_element:
                details['name'] = title_element.get_text().strip()

            # Extract image
            main_image = soup.select_one('.infobox img')
            if main_image and 'src' in main_image.attrs:
                img_src = main_image['src']
                if img_src.startswith('//'):
                    img_src = 'https:' + img_src
                details['image_url'] = img_src

            # Extract infobox details
            infobox = soup.select_one('.infobox, .vcard')
            if infobox:
                rows = infobox.select('tr')
                for row in rows:
                    header = row.select_one('th')
                    value = row.select_one('td')

                    if header and value:
                        header_text = header.get_text().strip().lower()
                        value_text = value.get_text().strip()

                        # Map common infobox fields
                        if any(term in header_text for term in ['born']):
                            details['birth_date'] = value_text
                        elif any(term in header_text for term in ['died']):
                            details['death_date'] = value_text
                        elif any(term in header_text for term in ['nationality', 'ethnicity', 'country']):
                            details['nationality'] = value_text
                        elif any(term in header_text for term in ['known for', 'notable work']):
                            details['notable_works'] = value_text
                        elif any(term in header_text for term in ['movement']):
                            details['art_movement'] = value_text
                        elif any(term in header_text for term in ['education', 'training', 'school']):
                            details['education'] = value_text
                        elif any(term in header_text for term in ['award']):
                            details['awards'] = value_text

            # Extract first paragraph as description
            for p in soup.select('#mw-content-text p'):
                if p.get_text().strip():
                    details['description'] = p.get_text().strip()
                    break

            # Clean up text fields (remove wiki annotations, excessive spaces, etc.)
            for key, value in details.items():
                if isinstance(value, str):
                    # Remove reference tags
                    value = re.sub(r'\[\d+\]', '', value)
                    # Normalize whitespace
                    value = re.sub(r'\s+', ' ', value).strip()
                    details[key] = value

            # Find links to other artwork pages
            self.extract_related_links(soup)

            return details

        except Exception as e:
            logging.error(f"Error extracting artist details from {artist_url}: {str(e)}")
            return details

    def extract_artwork_details(self, artwork_url):
        """
        Extract artwork details from Wikipedia.

        Args:
            artwork_url (str): URL of the artwork's Wikipedia page

        Returns:
            dict: Artwork details
        """
        logging.info(f"Extracting artwork details from: {artwork_url}")

        details = {
            'title': '',
            'artist': '',
            'artist_url': '',
            'year': '',
            'medium': '',
            'dimensions': '',
            'current_location': '',
            'description': '',
            'period_style': '',
            'art_movement': '',
            'image_url_compressed': '',
            'image_url': '',
            'license_info': '',
            'source_url': artwork_url
        }

        try:
            response = self.fetch_with_retry(artwork_url)
            soup = BeautifulSoup(response.text, 'html.parser')

            # Extract title
            title_element = soup.select_one('h1#firstHeading')
            if title_element:
                details['title'] = title_element.get_text().strip()

            # Extract main image
            main_image = soup.select_one('.infobox img, .thumb img')
            if main_image and 'src' in main_image.attrs:
                img_src = main_image['src']
                if img_src.startswith('//'):
                    img_src = 'https:' + img_src

                # Set the compressed image URL
                details['image_url_compressed'] = img_src

                # Try to find full resolution image
                if 'srcset' in main_image.attrs:
                    srcset = main_image['srcset'].split(',')
                    largest_img = srcset[-1].strip().split(' ')[0]
                    if largest_img.startswith('//'):
                        largest_img = 'https:' + largest_img
                    details['image_url'] = largest_img
                else:
                    details['image_url'] = img_src

            # Extract infobox details
            infobox = soup.select_one('.infobox, .vcard')
            if infobox:
                rows = infobox.select('tr')
                for row in rows:
                    header = row.select_one('th')
                    value = row.select_one('td')

                    if header and value:
                        header_text = header.get_text().strip().lower()
                        value_text = value.get_text().strip()

                        # Map common infobox fields
                        if any(term in header_text for term in ['artist', 'author', 'creator']):
                            details['artist'] = value_text
                            # Try to find artist link
                            artist_link = value.select_one('a')
                            if artist_link and artist_link.get('href', '').startswith('/wiki/'):
                                details['artist_url'] = urljoin(self.wikipedia_base_url, artist_link['href'])
                        elif any(term in header_text for term in ['year', 'date', 'created']):
                            details['year'] = value_text
                        elif any(term in header_text for term in ['medium', 'materials', 'type']):
                            details['medium'] = value_text
                        elif any(term in header_text for term in ['dimension', 'size', 'height', 'width']):
                            details['dimensions'] = value_text
                        elif any(term in header_text for term in ['location', 'museum', 'gallery', 'collection']):
                            details['current_location'] = value_text
                        elif any(term in header_text for term in ['period', 'style']):
                            details['period_style'] = value_text
                        elif any(term in header_text for term in ['movement']):
                            details['art_movement'] = value_text

            # Extract first paragraph as description
            for p in soup.select('#mw-content-text p'):
                if p.get_text().strip():
                    details['description'] = p.get_text().strip()
                    break

            # Clean up text fields
            for key, value in details.items():
                if isinstance(value, str):
                    # Remove reference tags
                    value = re.sub(r'\[\d+\]', '', value)
                    # Normalize whitespace
                    value = re.sub(r'\s+', ' ', value).strip()
                    details[key] = value

            # Find links to other artwork pages
            self.extract_related_links(soup)

            return details

        except Exception as e:
            logging.error(f"Error extracting artwork details from {artwork_url}: {str(e)}")
            return details

    def extract_related_links(self, soup):
        """
        Extract links that might be related to artworks or artists.

        Args:
            soup (BeautifulSoup): Parsed HTML content
        """
        # Look for links in the content area
        content_div = soup.select_one('#mw-content-text')
        if not content_div:
            return

        # Keywords that suggest the link might point to an artwork or artist
        art_keywords = ['painting', 'sculpture', 'artwork', 'portrait',
                        'museum', 'gallery', 'artist', 'painter']

        # Find related articles in the "See also" section
        see_also = soup.find('span', id='See_also')
        if see_also:
            see_also_section = see_also.find_parent('h2')
            if see_also_section:
                # Get the next UL after the "See also" heading
                next_ul = see_also_section.find_next('ul')
                if next_ul:
                    for li in next_ul.find_all('li'):
                        link = li.find('a')
                        if link and link.get('href', '').startswith('/wiki/'):
                            # Skip certain namespaces
                            if ':' in link.get('href') and not link.get('href').startswith('/wiki/Category:'):
                                continue
                            full_url = urljoin(self.wikipedia_base_url, link['href'])
                            if full_url not in self.processed_urls and full_url not in self.queue:
                                self.queue.append(full_url)

        # Find potentially related links in the content area
        for link in content_div.select('a[href^="/wiki/"]'):
            href = link.get('href')

            # Skip certain namespaces and special pages
            if ':' in href and not href.startswith('/wiki/Category:'):
                continue

            # Skip links that don't look like they point to artworks or artists
            link_text = link.get_text().lower()
            if not any(keyword in link_text for keyword in art_keywords) and not any(
                    keyword in href.lower() for keyword in art_keywords):
                continue

            full_url = urljoin(self.wikipedia_base_url, href)
            if full_url not in self.processed_urls and full_url not in self.queue:
                self.queue.append(full_url)

        # Find related categories
        category_links = soup.select('div.mw-normal-catlinks ul li a')
        for link in category_links:
            if link.get('href', '').startswith('/wiki/Category:'):
                category_url = urljoin(self.wikipedia_base_url, link['href'])
                # Get pages from this category
                self.add_category_pages_to_queue(category_url, limit=10)

    def add_category_pages_to_queue(self, category_url, limit=10):
        """
        Add pages from a category to the queue.

        Args:
            category_url (str): URL of the category
            limit (int): Maximum number of pages to add
        """
        try:
            category_name = category_url.split("/wiki/")[1]
            api_url = f"https://en.wikipedia.org/w/api.php?action=query&format=json&list=categorymembers&cmtitle={category_name}&cmlimit={limit}&cmnamespace=0"

            response = self.fetch_with_retry(api_url)
            category_data = response.json()

            if 'query' in category_data and 'categorymembers' in category_data['query']:
                for item in category_data['query']['categorymembers'][:limit]:
                    page_url = f"https://en.wikipedia.org/wiki/{item['title'].replace(' ', '_')}"
                    if page_url not in self.processed_urls and page_url not in self.queue:
                        self.queue.append(page_url)

        except Exception as e:
            logging.error(f"Error processing category {category_url}: {str(e)}")


    # Modified import_artwork method to handle artist creation failure
    def import_artwork(self, url):
        """
        Import artwork from Wikipedia URL.

        Args:
            url (str): URL of the Wikipedia page

        Returns:
            bool: True if successful, False otherwise
        """
        # Check if already processed
        if url in self.processed_urls:
            logging.info(f"Skipping already processed URL: {url}")
            return True

        try:
            # First, fetch the page and determine if it's an artwork or artist
            response = self.fetch_with_retry(url)
            soup = BeautifulSoup(response.text, 'html.parser')

            # Extract links for future processing regardless of page type
            self.extract_related_links(soup)

            # Mark as processed to avoid repeated attempts
            self.processed_urls.add(url)

            if self.is_artwork_page(soup):
                # Extract artwork details
                artwork_details = self.extract_artwork_details(url)

                if not artwork_details['title']:
                    logging.error(f"Could not extract title from {url}")
                    return False

                # Get or create artist
                artist_id = None
                if artwork_details['artist_url']:
                    # Extract artist details
                    artist_details = self.extract_artist_details(artwork_details['artist_url'])
                    artist_id = self.create_artist(artist_details)

                    # If artist creation fails, generate an ID based on the artist name
                    if not artist_id and artwork_details['artist']:
                        logging.warning(f"Artist creation failed for {artwork_details['artist']}, using generated ID")
                        artist_id = self.generate_id(artwork_details['artist'])
                else:
                    # Create a simple artist entry if we don't have a Wikipedia page
                    artist_name = artwork_details['artist']
                    if not artist_name:
                        artist_name = "Unknown Artist"

                    artist_id = self.create_artist({
                        'name': artist_name,
                        'wikipedia_url': '',
                        'description': f"Artist of {artwork_details['title']}"
                    })

                    # If artist creation fails, generate an ID based on the artist name
                    if not artist_id:
                        logging.warning(f"Artist creation failed for {artist_name}, using generated ID")
                        artist_id = self.generate_id(artist_name)

                if not artist_id:
                    logging.error(f"Could not create or generate artist ID for {artwork_details['title']}")
                    return False

                # Create artwork
                return self.create_artwork(artist_id, artwork_details)

            elif self.is_artist_page(soup):
                # Process as artist page
                artist_details = self.extract_artist_details(url)
                artist_id = self.create_artist(artist_details)

                if not artist_id:
                    # Generate an ID based on the artist name if creation fails
                    artist_name = artist_details.get('name')
                    if artist_name:
                        artist_id = self.generate_id(artist_name)
                        logging.warning(f"Artist creation failed for {artist_name}, using generated ID {artist_id}")
                    else:
                        logging.error(f"Could not create artist from {url} and no name available")
                        return False

                logging.info(f"Created artist: {artist_details['name']} from {url}")
                return True

            else:
                # Not an artwork or artist page, but still mark as processed
                logging.info(f"URL {url} is neither an artwork nor an artist page. Skipping.")
                return True

        except Exception as e:
            logging.error(f"Error importing from {url}: {str(e)}")
            return False

    def import_famous_artworks(self, limit=10):
        """
        Import a list of famous artworks from Wikipedia.

        Args:
            limit (int): Maximum number of artworks to import

        Returns:
            int: Number of successfully imported artworks
        """
        # List of famous artwork pages on Wikipedia
        famous_artworks = [
            "https://en.wikipedia.org/wiki/Mona_Lisa",
            "https://en.wikipedia.org/wiki/The_Starry_Night",
            "https://en.wikipedia.org/wiki/The_Persistence_of_Memory",
            "https://en.wikipedia.org/wiki/The_Night_Watch",
            "https://en.wikipedia.org/wiki/Girl_with_a_Pearl_Earring",
            "https://en.wikipedia.org/wiki/Guernica_(Picasso)",
            "https://en.wikipedia.org/wiki/The_Last_Supper_(Leonardo)",
            "https://en.wikipedia.org/wiki/The_Scream",
            "https://en.wikipedia.org/wiki/Water_Lilies",
            "https://en.wikipedia.org/wiki/The_Birth_of_Venus",
            "https://en.wikipedia.org/wiki/Las_Meninas",
            "https://en.wikipedia.org/wiki/The_Garden_of_Earthly_Delights",
            "https://en.wikipedia.org/wiki/Campbell%27s_Soup_Cans",
            "https://en.wikipedia.org/wiki/The_Son_of_Man",
            "https://en.wikipedia.org/wiki/American_Gothic",
            "https://en.wikipedia.org/wiki/The_Creation_of_Adam",
            "https://en.wikipedia.org/wiki/Impression,_Sunrise",
            "https://en.wikipedia.org/wiki/A_Sunday_Afternoon_on_the_Island_of_La_Grande_Jatte",
            "https://en.wikipedia.org/wiki/Whistler%27s_Mother",
            "https://en.wikipedia.org/wiki/The_Kiss_(Klimt)"
        ]

        # Add these to our queue to kick-start the process
        for url in famous_artworks:
            if url not in self.processed_urls and url not in self.queue:
                self.queue.append(url)

        self.current_sources.append("Famous Artworks")

        # Process the first batch
        count = 0

        for url in famous_artworks[:limit]:
            logging.info(f"Importing artwork {count + 1}/{limit}: {url}")
            if self.import_artwork(url):
                count += 1

            # Wait to avoid overloading servers
            time.sleep(self.delay)

        return count

    def import_artist_works(self, artist_name, limit=5):
        """
        Import artworks by a specific artist.

        Args:
            artist_name (str): Name of the artist
            limit (int): Maximum number of artworks to import

        Returns:
            int: Number of successfully imported artworks
        """
        # Search for the artist on Wikipedia
        search_url = f"https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch={artist_name}+artist&srlimit=1"

        try:
            response = self.fetch_with_retry(search_url)
            # response.raise_for_status()
            search_data = response.json()

            if 'query' in search_data and 'search' in search_data['query'] and search_data['query']['search']:
                artist_page_title = search_data['query']['search'][0]['title']
                artist_url = f"https://en.wikipedia.org/wiki/{artist_page_title.replace(' ', '_')}"

                # Extract artist details
                artist_details = self.extract_artist_details(artist_url)
                artist_id = self.create_artist(artist_details)

                if not artist_id:
                    logging.error(f"Could not create artist: {artist_name}")
                    return 0

                # Search for the artist's artworks
                artwork_search_url = f"https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch={artist_name}+painting+artwork&srlimit={limit}"

                artwork_response = requests.get(artwork_search_url)
                artwork_response.raise_for_status()
                artwork_data = artwork_response.json()

                count = 0
                if 'query' in artwork_data and 'search' in artwork_data['query']:
                    for item in artwork_data['query']['search']:
                        artwork_title = item['title']
                        artwork_url = f"https://en.wikipedia.org/wiki/{artwork_title.replace(' ', '_')}"

                        if artwork_url in self.processed_urls:
                            continue

                        logging.info(f"Importing artwork by {artist_name}: {artwork_title}")

                        # Extract artwork details
                        artwork_details = self.extract_artwork_details(artwork_url)

                        # Set artist info
                        artwork_details['artist'] = artist_details['name']
                        artwork_details['artist_url'] = artist_url

                        # Create artwork
                        if self.create_artwork(artist_id, artwork_details):
                            count += 1

                        # Wait to avoid overloading servers
                        time.sleep(self.delay)

                        # Save checkpoint periodically
                        if count % 5 == 0:
                            self.save_checkpoint()

                return count
            else:
                logging.error(f"Could not find artist: {artist_name}")
                return 0

        except Exception as e:
            logging.error(f"Error importing artworks for {artist_name}: {str(e)}")
            return 0

    def process_queue(self, batch_size=10, max_iterations=None):
        """
        Process URLs in the queue continuously.

        Args:
            batch_size (int): Number of URLs to process before saving checkpoint
            max_iterations (int): Maximum number of iterations (None for unlimited)

        Returns:
            int: Total number of artworks processed
        """
        total_processed = 0
        iteration = 0

        logging.info(f"Starting queue processing with {len(self.queue)} URLs in queue")

        while self.queue and (max_iterations is None or iteration < max_iterations):
            # Process a batch of URLs
            processed_in_batch = 0
            for _ in range(min(batch_size, len(self.queue))):
                if not self.queue:
                    break

                # Get the next URL from the queue
                url = self.queue.pop(0)

                # Skip if already processed
                if url in self.processed_urls:
                    continue

                logging.info(f"Processing URL: {url}")

                if self.import_artwork(url):
                    processed_in_batch += 1
                    total_processed += 1

                # Wait to avoid overloading servers
                time.sleep(self.delay)

            # Save checkpoint after each batch
            self.save_checkpoint()

            # Log progress
            iteration += 1
            logging.info(
                f"Completed batch {iteration}. Processed {processed_in_batch} artworks in this batch, {total_processed} total. {len(self.queue)} URLs remaining in queue.")

            # If queue is getting low, add some more sources
            if len(self.queue) < batch_size and len(self.current_sources) < 5:
                self.add_more_sources()

        return total_processed

    def add_more_sources(self):
        """Add more sources to the queue when it's running low"""
        try:
            # Add famous artists if not already done
            if "Famous Artists" not in self.current_sources:
                self.current_sources.append("Famous Artists")
                famous_artists = [
                    "Leonardo da Vinci", "Vincent van Gogh", "Pablo Picasso",
                    "Michelangelo", "Claude Monet", "Rembrandt", "Salvador Dali",
                    "Frida Kahlo", "Andy Warhol", "Georgia O'Keeffe", "Edward Hopper",
                    "Johannes Vermeer", "Edvard Munch", "Gustav Klimt", "Diego Rivera"
                ]
                for artist in famous_artists:
                    logging.info(f"Adding works of {artist} to queue")
                    self.import_artist_works(artist, limit=3)

            # Add art movements if not already done
            if "Art Movements" not in self.current_sources:
                self.current_sources.append("Art Movements")
                movements = [
                    "Impressionism", "Cubism", "Surrealism", "Abstract Expressionism",
                    "Renaissance Art", "Baroque", "Romanticism", "Post-Impressionism",
                    "Pop Art", "Expressionism"
                ]
                for movement in movements:
                    category_url = f"https://en.wikipedia.org/wiki/Category:{movement}"
                    logging.info(f"Adding works from movement: {movement}")
                    self.add_category_pages_to_queue(category_url, limit=5)

            # Add famous museums if not already done
            if "Museums" not in self.current_sources:
                self.current_sources.append("Museums")
                museums = [
                    "Louvre", "Metropolitan Museum of Art", "MoMA", "Tate Modern",
                    "Hermitage Museum", "Uffizi Gallery", "Prado Museum", "Guggenheim"
                ]
                for museum in museums:
                    search_url = f"https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch={museum}+artwork+collection&srlimit=5"
                    try:
                        response = self.fetch_with_retry(search_url)
                        search_data = response.json()

                        # ... rest of this section remains the same ...
                    except Exception as e:
                        logging.error(f"Error adding museum {museum}: {str(e)}")

                # Save updated sources list
                self.save_checkpoint()

        except Exception as e:
            logging.error(f"Error adding more sources: {str(e)}")


def main():
    """Main function to run the artwork importer"""
    import argparse

    parser = argparse.ArgumentParser(description='Import artwork data from Wikipedia')
    parser.add_argument('--base-url', type=str, default='http://localhost:7040', help='Base URL of the API')
    parser.add_argument('--delay', type=float, default=1.0, help='Delay between requests in seconds')
    parser.add_argument('--batch-size', type=int, default=10, help='Number of URLs to process before saving checkpoint')
    parser.add_argument('--max-iterations', type=int, default=None,
                        help='Maximum number of iterations (None for unlimited)')
    parser.add_argument('--checkpoint-file', type=str, default='artwork_checkpoint.pkl',
                        help='File to store checkpoint data')

    args = parser.parse_args()

    try:
        importer = ArtworkImporter(
            base_url=args.base_url,
            delay=args.delay,
            checkpoint_file=args.checkpoint_file
        )

        # Start with famous artworks if queue is empty
        if not importer.queue:
            importer.import_famous_artworks(limit=10)

        # Process queue continuously
        importer.process_queue(batch_size=args.batch_size, max_iterations=args.max_iterations)

    except KeyboardInterrupt:
        logging.info("Program interrupted by user. Saving checkpoint...")
        importer.save_checkpoint()

    except Exception as e:
        logging.error(f"Unexpected error: {str(e)}")
        if 'importer' in locals():
            importer.save_checkpoint()

    logging.info("Artwork import completed")


if __name__ == "__main__":
    main()